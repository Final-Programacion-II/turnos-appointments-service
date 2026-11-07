# Decisiones de diseño

Registro de las decisiones técnicas del proyecto, con el motivo y las alternativas descartadas.

## 1. JHipster como base de los servicios

Los dos servicios se generan con JHipster como aplicaciones `monolith` sin cliente
(`skipClient`), con autenticación JWT.

- Motivo: resuelve seguridad JWT, migraciones con Liquibase y configuración de tests,
  y deja el foco en la sincronización y el flujo de reservas.
- Descartado: el tipo `microservice` de JHipster, porque requiere gateway y registry
  (Consul), que el sistema no necesita y agregan infraestructura sin aportar al objetivo.

## 2. PostgreSQL, una instancia por servicio

Cada servicio tiene su propio contenedor de PostgreSQL y sus propias migraciones.
Ningún servicio accede a la base del otro.

- Motivo: la separación de datos queda garantizada por la infraestructura y no solo
  por convención.
- Descartado: dos esquemas en un mismo servidor. Cumple el requisito, pero la
  separación depende de la disciplina y no de la arquitectura.

## 3. Los usuarios pertenecen al servicio de turnos

El registro y el login del usuario final viven en `turnos-appointments-service`.
El `externalPatientId` que se envía a la cátedra es el id interno y estable del usuario.
El servicio de catálogo no guarda usuarios: valida el JWT con el mismo secreto.

- Motivo: la propiedad de las reservas se controla en turnos, donde está la identidad
  del usuario.
- Descartado: un servicio de autenticación separado, porque agrega un tercer servicio
  que el enunciado no pide.

## 4. Turnos consulta al catálogo propagando el JWT del usuario

Las consultas de disponibilidad siempre las inicia un usuario autenticado, por lo que
turnos reenvía su token al catálogo.

- Motivo: es simple, no requiere credenciales adicionales y cada llamada queda asociada
  a un usuario.
- Descartado: un token técnico interno entre servicios. Se reconsidera si aparece
  alguna llamada que no dependa de un usuario.
- Cómo se valida: los dos servicios firman y validan los JWT con el mismo secreto
  (HMAC), configurado con la variable `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64SECRET`.
  El catálogo verifica firma y vencimiento del token que le llega, igual que si lo
  hubiera emitido él. Si el secreto no coincide, responde 401 y turnos no puede seguir.
- Limitación: con un secreto compartido, cualquiera de los dos servicios puede emitir
  tokens válidos para el otro. Por eso el login de ejemplo del catálogo (`admin/admin`
  del perfil dev) se tiene que sacar antes de la entrega: los usuarios se autentican
  solo en turnos.
- Si el catálogo no responde (timeout de 2 s para conectar y 5 s para leer), turnos no
  empieza la operación: sin la agenda vigente no puede validar el turno.

## 5. Stack

- JHipster 9.2.0 con Spring Boot 4, Java 21 y Maven Wrapper (`./mvnw`).
- PostgreSQL, con la versión definida por la configuración Docker de JHipster.
- Node 22 LTS, usado solo por las herramientas de JHipster.
- Kafka con `spring-kafka`; Redis con Lettuce.
- App: Kotlin Multiplatform, con librerías a definir antes de empezar la app.
- Secretos (JWT técnico, credenciales de Redis y Kafka) solo en variables de entorno,
  nunca en el repositorio.

## 6. Contraseñas con Argon2id

Las contraseñas se guardan con Argon2id (`Argon2PasswordEncoder` de Spring Security:
16 MiB de memoria, 2 iteraciones, paralelismo 1).

- Motivo: bcrypt hace lento cada intento, pero usa muy poca memoria, así que una GPU
  puede probar miles en paralelo. Argon2id obliga a usar memoria en cada intento y eso
  limita el paralelismo. Es la primera recomendación de OWASP.
- Se usa `DelegatingPasswordEncoder`: los hashes nuevos llevan el prefijo `{argon2}` y
  los que no tienen prefijo (los usuarios de ejemplo de JHipster, en bcrypt) se siguen
  aceptando. En el primer login correcto, Spring Security vuelve a guardar la contraseña
  con Argon2id (`DomainUserDetailsService#updatePassword`).
- La columna `password_hash` pasa de 60 a 255 caracteres, en un changeset nuevo.
- Descartado: cambiar solo el encoder. Los hashes bcrypt existentes dejarían de
  funcionar y habría que regenerarlos a mano.

## 7. El usuario queda activo al registrarse

JHipster crea los usuarios desactivados y les manda un mail con un enlace de activación.
El sistema no tiene servidor de correo, así que el registro deja al usuario activo y no
manda el mail.

- El estado de activación lo sigue decidiendo el backend: el cliente no puede mandarlo.
- Consecuencia: un login o un email repetido se rechaza de inmediato. Antes, un usuario
  sin activar se podía reemplazar con un registro nuevo.
- Limitación: no se verifica que el email sea del usuario. Se puede agregar más adelante
  con un servidor SMTP.

## 8. Proceso de reserva con estados locales

Cada reserva se guarda en `reservation_process` desde que la cátedra crea el hold, con un
estado propio (`HELD`, `WAITING_FOR_PHONE`, `CONFIRMED`, etc.). El detalle está en
[reservas.md](reservas.md).

- Motivo: las respuestas llegan por REST y por Kafka, en cualquier orden y a veces
  repetidas. Con el estado guardado, cada respuesta se aplica solo si tiene sentido, y un
  resultado (confirmada, vencida, inválida) nunca vuelve atrás.
- Los cambios de estado usan un bloqueo de fila (`SELECT ... FOR UPDATE`) en una
  transacción corta, para que una respuesta REST y un evento Kafka simultáneos no se pisen.
- Las llamadas a la cátedra se hacen fuera de las transacciones de la base.
- Si la confirmación inicial no llega, el proceso queda `HELD` y el usuario puede
  reintentar. No hay reintentos automáticos.
- Descartado: bloqueo optimista con `@Version`. También evita que se pisen, pero el que
  pierde tiene que volver a leer y reintentar, y eso suma código en el listener de Kafka
  sin ventajas con el poco tráfico que tiene cada proceso.

## 9. Eventos de Kafka aplicados una sola vez

Los eventos de reservas se aplican con un registro de `eventId` en la tabla
`processed_reservation_event`, guardado en la misma transacción que el cambio de estado.

- Motivo: Kafka entrega al menos una vez. Si el `eventId` se guardara aparte, una caída
  entre los dos pasos podría dejar el cambio sin registrar (y aplicarlo dos veces) o el
  registro sin el cambio (y perder el evento).
- El `eventId` se consulta después de bloquear el proceso. Así, dos copias del mismo evento
  que llegan juntas se procesan una detrás de la otra, y la segunda se reconoce como repetida.
- El teléfono se publica primero y el estado se cambia después. Si se cambiara antes y el
  envío fallara, habría que volver el estado atrás.
- Descartado: confiar solo en que el dominio ignora los eventos repetidos. Sirve para casi
  todos, pero no para un rechazo de teléfono repetido, que volvería a pedir un teléfono que
  ya se mandó.

## 10. Reconciliación periódica por REST

Además de Kafka, turnos consulta cada minuto el listado de reservas de la cátedra para los
procesos abiertos que ya vencieron, y aplica su estado real. El detalle está en
[reservas.md](reservas.md).

- Motivo: Kafka es la vía principal, pero un evento perdido dejaría un proceso abierto para
  siempre. La referencia pide reconciliar por REST con los ids guardados (sección 18.4), y el
  enunciado pide recuperar procesos pendientes al reiniciar.
- Corre al arrancar, así se cierran los procesos que vencieron con el servicio apagado.
- Espera un minuto después del vencimiento, para no adelantarse a Kafka.
- Se acota: 50 procesos por pasada, una consulta por usuario y sin reintentos dentro de la
  misma pasada.
- Descartado: consultar a la cátedra cada vez que el usuario pide su reserva. Haría depender
  cada consulta de la cátedra, y el enunciado pide que lo propio se pueda atender aunque un
  servicio externo no responda.

## 11. Docker Compose del sistema completo

`docker/compose.yml`, en este repo, levanta los dos servicios y sus dos PostgreSQL con las
imágenes que genera Jib. El detalle está en [docker.md](docker.md).

- Motivo: el enunciado pide levantar los dos backends y sus bases con Docker Compose. Va en
  el repo de turnos porque turnos es el que depende del catálogo; el catálogo se puede seguir
  levantando solo con su propio `src/main/docker/app.yml`.
- Cada base está en una red interna propia y sin puertos publicados: la separación de datos
  no depende de la configuración de cada servicio, sino de que no existe la ruta.
- Las subredes son fijas en `10.42.x`, porque la cátedra está en `172.17.201.1`, dentro del
  rango que Docker usa por defecto.
- El healthcheck usa `readiness`, que no incluye a la cátedra. Si se incluyera, una caída de
  Redis o Kafka haría que turnos no arranque, aunque puede atender todo lo propio.
- Los secretos van en `docker/.env`, que no se sube al repositorio.
- No se usa el perfil `secret-samples` de JHipster, que trae un secreto JWT de ejemplo.
- Descartado: un compose en cada repo que se conecten entre sí. Obliga a crear redes a mano y
  a levantar todo en un orden fijo.

