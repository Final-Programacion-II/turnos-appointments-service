# Proceso de reserva

Reservar un turno no es una sola llamada: la cátedra primero retiene el turno, después
pide un teléfono por Kafka y recién ahí confirma. El servicio de turnos guarda cada paso
en la tabla `reservation_process`, así el usuario puede ver en qué quedó su reserva y
nada se pierde si algo falla en el medio.

## Cómo empieza

`POST /api/reservations` con el profesional, la fecha, la hora y el nombre del paciente:

1. Se calcula la disponibilidad con la agenda vigente del catálogo y las ocupaciones de
   la cátedra. Si el turno no está libre, se responde 409 `SLOT_NOT_AVAILABLE` y no se
   llama a la cátedra.
2. Se pide el hold a la cátedra. Si lo rechaza (por ejemplo, otro lo tomó recién), se
   devuelve el mismo status y el mismo `code` que mandó la cátedra, y no se guarda nada.
3. Se guarda el proceso como `HELD`, con los ids que dio la cátedra.
4. Se hace la confirmación inicial. Si sale bien, el proceso pasa a `WAITING_FOR_PHONE`.

Se guarda antes de confirmar a propósito: la confirmación hace que la cátedra publique
el pedido de teléfono, y ese evento puede llegar por Kafka antes que la respuesta REST.
Así, cuando llega, ya hay un proceso al que aplicárselo.

## Estados

```mermaid
stateDiagram-v2
    [*] --> HELD: hold creado
    HELD --> WAITING_FOR_PHONE: confirmación inicial o pedido de teléfono
    HELD --> EXPIRED: HOLD_EXPIRED
    HELD --> FAILED: otro rechazo
    WAITING_FOR_PHONE --> PHONE_SUBMITTED: el usuario manda el teléfono
    PHONE_SUBMITTED --> WAITING_FOR_PHONE: teléfono rechazado
    WAITING_FOR_PHONE --> CONFIRMED
    PHONE_SUBMITTED --> CONFIRMED: AppointmentConfirmed
    WAITING_FOR_PHONE --> EXPIRED
    PHONE_SUBMITTED --> EXPIRED: AppointmentProcessExpired
    PHONE_SUBMITTED --> INVALID: AppointmentProcessInvalid
    CONFIRMED --> CANCELLED: AppointmentCancelled
```

`HELD`, `WAITING_FOR_PHONE` y `PHONE_SUBMITTED` son estados abiertos. Los demás son
resultados, y un resultado no se reabre: si llega un aviso tardío o repetido, se ignora.
La única salida de un resultado es cancelar una reserva `CONFIRMED`.

Las reglas están en `ReservationProcess` (dominio) y tienen sus propios tests.

## Cuando la cátedra no responde

Un timeout no dice si la operación se hizo o no (sección 18.4 de la referencia).

| Paso | Qué pasa si la cátedra no responde |
| --- | --- |
| Hold | 503 `CATEDRA_UNAVAILABLE` y no se guarda nada. Si el hold sí se creó, vence solo en unos minutos; mientras tanto ese turno da `SLOT_ALREADY_HELD`. |
| Confirmación inicial | El proceso queda `HELD` y se responde 201 igual. La app puede reintentar con `POST /api/reservations/{id}/confirm`. |
| Reintento de la confirmación | Si la primera sí había llegado, la cátedra responde `RESERVATION_PROCESS_ALREADY_CONFIRMED` y el proceso avanza igual a `WAITING_FOR_PHONE`. |

El reintento lo pide el usuario: no hay reintentos automáticos que puedan quedar en un
ciclo sin fin.

## Respuestas que llegan juntas

Una respuesta REST y un evento Kafka pueden querer cambiar el mismo proceso al mismo
tiempo. Para que no se pisen, cada cambio lee el proceso con `SELECT ... FOR UPDATE`, le
aplica la regla y lo guarda en la misma transacción. El que llega segundo espera y ve el
estado que dejó el primero.

Las llamadas a la cátedra se hacen fuera de esas transacciones: una cátedra lenta no deja
registros bloqueados.

## Quién ve cada reserva

El dueño sale siempre del JWT (claim `userId`), nunca del body. Un usuario que pide una
reserva ajena recibe 404 `RESERVATION_NOT_FOUND`, lo mismo que si no existiera, para no
revelar qué ids existen.

A la cátedra se le manda `externalPatientId = "user-{id}"`, armado con el id interno del
usuario, que no cambia aunque cambie su login o su email.

## Teléfono y resultados (Kafka)

Después de la confirmación inicial, el resto del proceso va por Kafka:

```mermaid
sequenceDiagram
    participant App as App
    participant T as Turnos
    participant KA as Kafka acciones
    participant KP as Kafka teléfono

    KA-->>T: AdditionalInformationRequested (eventId)
    Note over T: guarda el eventId como requestEventId
    App->>T: POST /api/reservations/{id}/phone
    T->>KP: AdditionalInformationSubmitted (requestEventId, teléfono)
    Note over T: PHONE_SUBMITTED
    KA-->>T: AppointmentConfirmed / Rejected / Expired / Invalid
    App->>T: GET /api/reservations/{id}
```

**Recibir eventos** (`CatedraActionsListener`):

- Cada evento se aplica una sola vez. En la misma transacción se bloquea el proceso, se
  aplica el cambio y se guarda el `eventId` en `processed_reservation_event`. Si el evento
  llega de nuevo, se reconoce por el `eventId` y se ignora.
- Si el evento no corresponde al estado actual (por ejemplo, un vencimiento que llega
  después de la confirmación), el dominio no lo aplica.
- Un mensaje que no se puede leer, o de un tipo desconocido, se registra en el log y se
  saltea: reintentarlo no lo arregla.
- Si falla la base, Spring Kafka reintenta 5 veces, con 2 segundos entre intentos. Después
  lo registra y sigue con el próximo, para no trabar el topic.
- Con un consumer group nuevo, se lee el topic desde el principio. Así no se pierden los
  eventos que llegaron mientras el servicio estaba apagado.

**Mandar el teléfono** (`POST /api/reservations/{id}/phone`):

1. Se revisa que la cátedra ya lo haya pedido y que el proceso no haya vencido. Si no,
   409 `INVALID_RESERVATION_STATE`.
2. Se limpia y se valida igual que lo hace la cátedra: se sacan espacios, guiones,
   paréntesis y puntos, y tiene que quedar `^\+?[0-9]{7,15}$`. Si no, 400
   `INVALID_PHONE_NUMBER`, sin gastar un intento.
3. Se publica en Kafka y se espera la confirmación del envío (hasta 10 segundos). La
   message key es el `reservationProcessId`.
4. Recién ahí el proceso pasa a `PHONE_SUBMITTED`. Si Kafka no responde, se devuelve 503 y
   el proceso sigue esperando el teléfono, así que se puede volver a intentar.

Cada envío lleva un `eventId` nuevo, como pide la cátedra para mandar otro teléfono después
de un rechazo.

## Reconciliación por REST

Si un resultado no llega por Kafka (un evento perdido, el servicio apagado más tiempo del que
guarda el topic, la cátedra que no publica), el proceso quedaría abierto para siempre. Para
evitarlo, `ReconciliationScheduler` corre a los 20 segundos de arrancar y después cada minuto:

1. Busca los procesos abiertos que vencieron hace más de un minuto. El minuto de margen es
   para no adelantarse a Kafka, que normalmente avisa en segundos.
2. Hace una consulta por usuario a `GET /api/appointments?externalPatientId=...`. No usa
   `from`/`to`, porque la cátedra responde 500 con esos filtros.
3. Aplica lo que dice la cátedra:

| Estado en la cátedra | Qué pasa con el proceso |
| --- | --- |
| `CONFIRMED` | `CONFIRMED` |
| `CANCELLED` | `CONFIRMED` y después `CANCELLED` |
| `FAILED` | `EXPIRED` (motivo `FAILED`) |
| `PHONE_PENDING` | Nada: la cátedra todavía no lo cerró |
| No aparece y el proceso está `HELD` | `EXPIRED` (motivo `HOLD_EXPIRED`): la confirmación inicial nunca llegó |

Usa las mismas reglas del dominio que los eventos, así que no reabre resultados. Si el evento
llega mientras tanto, el bloqueo de fila hace que uno espere al otro y el segundo no cambia
nada. Si la cátedra no responde, esos procesos se revisan en la pasada siguiente. Cada pasada
revisa como máximo 50 procesos.

## Mis reservas y cancelación

**Listado** (`GET /api/reservations`): se lee solo de la base de turnos, sin llamar al
catálogo ni a la cátedra. Así funciona aunque alguno de los dos no responda (sección 8 del
enunciado). Viene del turno más lejano al más cercano, se puede filtrar por uno o más
`status` y se pagina con `page` y `size` (máximo 100). El total va en el header
`X-Total-Count`.

**Cancelación** (`POST /api/reservations/{id}/cancel`, con `reason` opcional, de hasta 500
caracteres):

1. Solo el dueño, y solo si la reserva está `CONFIRMED`. Si no, 409
   `INVALID_RESERVATION_STATE`. Si ya está `CANCELLED`, se devuelve como está.
2. Se cancela primero en la cátedra (`POST /api/appointments/{reservationProcessId}/cancel`) y,
   si salió, acá. Si la cátedra no responde, la reserva sigue confirmada y se puede volver a
   intentar sin riesgo, porque en la cátedra cancelar es idempotente.
3. El `AppointmentCancelled` que llega después por Kafka no cambia nada: la reserva ya está
   cancelada.

## Endpoints

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/reservations` | Reservas propias, paginadas y filtrables por `status` |
| POST | `/api/reservations` | Empieza una reserva (hold + confirmación inicial) |
| GET | `/api/reservations/{id}` | Estado de una reserva propia |
| POST | `/api/reservations/{id}/confirm` | Reintenta la confirmación inicial si quedó `HELD` |
| POST | `/api/reservations/{id}/phone` | Manda el teléfono que pidió la cátedra |
| POST | `/api/reservations/{id}/cancel` | Cancela una reserva confirmada propia |

En la respuesta, `phoneRequested: true` indica que la app tiene que pedir el teléfono.

## Prueba contra la cátedra (2/11)

Con Romina Acosta (id local 1068, id de la cátedra 218), martes 3/11 a las 13:00:

- La reserva se creó con 201 y quedó `WAITING_FOR_PHONE`, con 10 minutos de vigencia.
- Consultada por su dueño, respondió 200. Consultada por otro usuario, 404 `RESERVATION_NOT_FOUND`.
- Pedir el mismo turno otra vez dio 409 `SLOT_ALREADY_HELD`: lo rechazó la cátedra y no se
  guardó nada.
- **Hallazgo:** con el proceso en `WAITING_FOR_PHONE` y el hold vigente,
  `GET /api/appointment-occupancies` devolvió la lista vacía. Por eso la disponibilidad
  siguió mostrando las 13:00 como libres. El turno no se puede tomar dos veces, porque la
  cátedra rechaza el segundo hold, pero el usuario se entera recién al intentar reservarlo.
  Se consultó a la cátedra si es el comportamiento esperado.

## Prueba de Kafka contra la cátedra (3/11 y 4/11)

- **3/11:** turnos se suscribió a `alumnos.turnos.acciones.turnos-facundo-mangione`, pero no
  llegó ningún evento. Con un cliente independiente se comprobó que los 18 topics de acciones
  y los 18 de catálogo de todos los alumnos estaban vacíos, aunque la cátedra tenía las
  reservas en `PHONE_PENDING` y después en `FAILED`. Se le avisó al profesor.
- **4/11:** la cátedra publicó los eventos pendientes. Al levantar turnos, las cuatro reservas
  de prueba pasaron solas a `EXPIRED` con `HOLD_EXPIRED`.
- **Flujo completo (reserva 1201, Romina Acosta, 10/11, 14:30):** pedido de teléfono →
  `PHONE_SUBMITTED` → `CONFIRMED` en unos 4 segundos. Después, las 14:30 dejaron de aparecer
  en la disponibilidad.
- **Eventos repetidos:** se levantó turnos con un consumer group nuevo, que leyó el topic
  desde el principio. Los 10 eventos se reconocieron como ya aplicados y no cambió nada.
- `GET /api/appointments` con `from`/`to` sigue respondiendo 500. Sin esos filtros, o con
  `externalPatientId`, responde bien.
