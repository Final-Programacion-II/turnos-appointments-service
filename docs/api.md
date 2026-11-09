# API y contratos

Esta es la API de turnos para la app, más el contrato con el catálogo. El catálogo documenta
su propia API en su repo.

## Convenciones

- Base: `http://localhost:8082` (turnos) y `http://localhost:8081` (catálogo).
- Salvo el registro y el login, todo pide `Authorization: Bearer <jwt>`. El JWT lo emite
  turnos al iniciar sesión, y vale en los dos servicios.
- JSON en `camelCase`. Fechas `yyyy-MM-dd`. Las horas se mandan como `HH:mm` y vuelven como
  `HH:mm:ss`. Los instantes van en UTC (ISO-8601).
- Los errores son `application/problem+json`, con `status`, `title`, `detail` y un `code`
  estable. La app tiene que decidir por `status` y `code`, nunca por el texto de `detail`.
  Los errores de validación traen además `fieldErrors`.

```json
{
  "status": 409,
  "title": "Conflict",
  "detail": "El turno del 2026-11-10 a las 13:00 no está disponible",
  "code": "SLOT_NOT_AVAILABLE"
}
```

## Cuenta

| Método | Ruta | Auth | Qué hace |
| --- | --- | --- | --- |
| POST | `/api/register` | No | Registra un usuario. 201. |
| POST | `/api/authenticate` | No | Inicia sesión. 200 con `{ "id_token": "..." }`, o 401. |
| GET | `/api/account` | Sí | Datos del usuario del token |
| POST | `/api/account/change-password` | Sí | Cambia la contraseña |

Registro (los mismos campos y límites que el usuario de JHipster):

```json
{
  "login": "ana",
  "password": "clave1234",
  "firstName": "Ana",
  "lastName": "Lopez",
  "email": "ana@example.com",
  "imageUrl": null,
  "langKey": "es"
}
```

- `login`: de 1 a 50 caracteres (letras, números y `_.@-`). `password`: de 4 a 100.
- `email`: válido, de 5 a 254. `firstName` y `lastName`: hasta 50. `imageUrl`: opcional.
- El usuario queda activo enseguida. El id, la activación y los roles los decide el backend.
- Un login o un email repetido da 400.

Login: `{ "username": "ana", "password": "clave1234", "rememberMe": false }`. El token dura
24 horas, o 30 días con `rememberMe`. Cualquier falla da el mismo 401.

## Disponibilidad

`GET /api/availability?professionalId=1067&date=2026-11-10`

`professionalId` es el id del catálogo (el que devuelve la búsqueda).

```json
{
  "professionalId": 1067,
  "professionalName": "Romina Acosta",
  "categoryName": "Ginecologia",
  "date": "2026-11-10",
  "bookable": true,
  "slots": [
    { "startTime": "13:00:00", "endTime": "13:30:00" },
    { "startTime": "13:30:00", "endTime": "14:00:00" }
  ]
}
```

Si el profesional o su categoría están deshabilitados, viene `bookable: false` y sin turnos.
Una fecha pasada da 400.

## Reservas

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/reservations?status=&page=0&size=20` | Mis reservas, del turno más lejano al más cercano. El total va en `X-Total-Count`. |
| POST | `/api/reservations` | Empieza una reserva: hold y confirmación inicial. 201. |
| GET | `/api/reservations/{id}` | Una reserva propia |
| POST | `/api/reservations/{id}/confirm` | Reintenta la confirmación inicial, si quedó `HELD` |
| POST | `/api/reservations/{id}/phone` | Manda el teléfono que pidió la cátedra |
| POST | `/api/reservations/{id}/cancel` | Cancela una reserva confirmada propia |

Empezar una reserva:

```json
{
  "professionalId": 1067,
  "date": "2026-11-10",
  "startTime": "13:00",
  "patientFirstName": "Ana",
  "patientLastName": "Lopez"
}
```

Los nombres del paciente van de 2 a 100 caracteres. Teléfono: `{ "phoneNumber": "+54 9 261 555-1234" }`,
con 7 a 15 dígitos y `+` opcional; se aceptan espacios, guiones, paréntesis y puntos.
Cancelación: `{ "reason": "No puedo ir" }`, opcional y de hasta 500 caracteres.

Todas las operaciones de reservas responden con la reserva:

```json
{
  "id": 1051,
  "status": "WAITING_FOR_PHONE",
  "professionalId": 1067,
  "professionalName": "Romina Acosta",
  "categoryName": "Ginecologia",
  "date": "2026-11-10",
  "startTime": "13:00:00",
  "endTime": "13:30:00",
  "patientFirstName": "Ana",
  "patientLastName": "Lopez",
  "expiresAt": "2026-11-06T19:09:46Z",
  "phoneRequested": true,
  "statusReason": null
}
```

Cómo la usa la app:

1. Después del `POST`, consultar la reserva hasta que `phoneRequested` sea `true`, y ahí pedir
   el teléfono. Si quedó `HELD`, ofrecer reintentar con `/confirm`.
2. Después de mandar el teléfono, consultar hasta que llegue un resultado: `CONFIRMED`,
   `EXPIRED` o `INVALID`. Si vuelve a `WAITING_FOR_PHONE` con `statusReason`, la cátedra rechazó
   el teléfono y se puede mandar otro.

Los estados y el proceso completo están en [reservas.md](reservas.md). Una reserva ajena
siempre da 404, igual que una que no existe.

## Errores de turnos

| HTTP | `code` | Cuándo |
| --- | --- | --- |
| 400 | `INVALID_REQUEST` | Fecha pasada o paginación fuera de rango |
| 400 | `INVALID_PHONE_NUMBER` | Teléfono con formato inválido |
| 400 | (validación, con `fieldErrors`) | Falta un campo o no cumple su límite |
| 401 | | Sin token, token vencido o login fallido |
| 404 | `PROFESSIONAL_NOT_FOUND` | El profesional no existe en el catálogo |
| 404 | `RESERVATION_NOT_FOUND` | La reserva no existe o es de otro usuario |
| 409 | `SLOT_NOT_AVAILABLE` | El turno no está entre los libres |
| 409 | `INVALID_RESERVATION_STATE` | La operación no corresponde al estado (por ejemplo, cancelar una que no está confirmada) |
| 4xx | el de la cátedra | La cátedra rechazó el pedido: `SLOT_ALREADY_HELD`, `SLOT_ALREADY_RESERVED`, `INVALID_SLOT`, `PROFESSIONAL_DISABLED`, `RESERVATION_NOT_CANCELLABLE`... |
| 503 | `CATALOG_UNAVAILABLE` | El catálogo no respondió |
| 503 | `CATEDRA_UNAVAILABLE` | La API o el Kafka de la cátedra no respondieron. Se puede reintentar. |

## Contrato turnos → catálogo

Es la única llamada entre los dos servicios. Turnos la hace antes de calcular la disponibilidad
y antes de reservar, para trabajar siempre con la agenda vigente.

```http
GET http://catalog:8081/api/professionals/{id}/agenda
Authorization: Bearer <jwt del usuario que hizo el pedido>
```

Respuesta 200:

```json
{
  "id": 1067,
  "externalId": 218,
  "firstName": "Romina",
  "lastName": "Acosta",
  "categoryName": "Ginecologia",
  "bookable": true,
  "catalogVersion": 10,
  "schedules": [
    { "externalId": 332, "dayOfWeek": "TUESDAY", "startTime": "13:00:00", "endTime": "16:00:00", "slotDurationMinutes": 30 }
  ]
}
```

- `id` es el id local del catálogo; `externalId` es el de la cátedra. Turnos usa `externalId`
  para hablar con la cátedra.
- `bookable` es `true` si el profesional y su categoría están habilitados.
- `schedules` trae solo los horarios habilitados.
- `catalogVersion` es la versión de la copia local con la que se respondió.

Autenticación: turnos reenvía el JWT del usuario. El catálogo valida firma y vencimiento con
el secreto compartido. Así cada llamada queda asociada a un usuario (ver
[seguridad.md](seguridad.md)).

| Respuesta del catálogo | Qué hace turnos |
| --- | --- |
| 200 | Sigue con la agenda |
| 404 | 404 `PROFESSIONAL_NOT_FOUND` |
| Otro error, timeout (2 s para conectar, 5 s para leer) o sin conexión | 503 `CATALOG_UNAVAILABLE`, y no empieza la operación |

Si el catálogo no responde, turnos igual sigue atendiendo lo que es solo suyo: consultar,
listar y cancelar reservas no llaman al catálogo.

## API del catálogo (resumen)

La app usa estos endpoints del catálogo, todos con el JWT de turnos:

| Método | Ruta | Qué hace |
| --- | --- | --- |
| GET | `/api/professionals/search?categoryId=&name=&enabled=&day=&from=&to=` | Búsqueda con filtros sobre la copia local |
| GET | `/api/professionals`, `/api/professionals/{id}` | Profesionales |
| GET | `/api/professional-categories`, `/api/professional-categories/{id}` | Categorías |
| GET | `/api/weekly-schedules`, `/api/weekly-schedules/{id}` | Horarios semanales |
| GET | `/api/professionals/{id}/agenda` | Agenda vigente (la del contrato de arriba) |
| GET | `/api/catalog/sync-status` | Versión local, última sincronización y último error |

El catálogo es de solo lectura: no tiene endpoints para crear ni modificar datos. Los datos
llegan de la cátedra.
