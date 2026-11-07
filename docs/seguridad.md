# Seguridad

Decisiones y limitaciones de seguridad del sistema (sección 9 del enunciado). Cada punto
dice qué se hace y dónde está.

## Usuarios y contraseñas

- Los usuarios finales se registran e inician sesión solo en turnos (`/api/register` y
  `/api/authenticate`). El catálogo no tiene usuarios ni login.
- Las contraseñas se guardan con **Argon2id** y nunca se devuelven ni se escriben en los
  logs. Los hashes viejos en bcrypt se migran solos en el primer login (decisiones, punto 6).
- El registro valida los datos con las mismas reglas que JHipster. El cliente no puede elegir
  el id, el estado de activación ni los roles: los asigna el backend.
- JHipster carga los usuarios de ejemplo `admin/admin` y `user/user` en todas las bases. Sus
  contraseñas son públicas, así que en producción (perfil `prod`, el que usa Docker) un
  changeset los desactiva y no pueden iniciar sesión
  (`20261107000000_disable_default_users.xml`). En desarrollo y en los tests siguen activos.
- Un login fallido siempre da 401 con el mismo mensaje, sea por contraseña incorrecta, por un
  usuario que no existe o por un usuario desactivado. JHipster mostraba la causa ("User x was
  not found in the database", o un 500 con "User x was not activated"), y eso dejaba averiguar
  qué logins existen. Se corrigió en `ExceptionTranslator` (probado el 7/11 en Docker).

## JWT

- Al iniciar sesión, turnos emite un JWT firmado con HS512. Dura 24 horas, o 30 días con
  "recordarme". Además del login y los roles, lleva el claim `userId`.
- Todas las rutas bajo `/api/**` piden un JWT válido, salvo el registro, el login y la
  recuperación de contraseña. De `/management` solo son públicos `health` e `info`.
  Lo demás pide el rol `ADMIN`.
- Turnos valida firma y vencimiento en cada pedido (Spring Security, resource server).

## Comunicación entre los dos servicios

- Turnos le reenvía al catálogo el JWT del usuario que hizo el pedido (decisiones, punto 4).
  Así cada llamada queda asociada a un usuario, y el catálogo valida firma y vencimiento como
  si el token fuera suyo.
- Los dos servicios comparten el secreto HMAC (`JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64SECRET`).
  La consecuencia es que cualquiera de los dos podría emitir tokens válidos para el otro. Para
  que eso no sea un problema, el catálogo **no emite tokens**: no tiene login.
- En Docker, los servicios se hablan por una red interna (`http://catalog:8081`). Las bases
  están en redes propias, sin ruta desde el otro servicio (ver `docker.md`).

## Autorización por usuario

- El dueño de una reserva sale siempre del claim `userId` del token, nunca del body ni de un
  parámetro.
- Consultar, reintentar, mandar el teléfono o cancelar una reserva ajena da **404**, igual que
  si no existiera. Así no se revela qué ids existen.
- El listado de reservas filtra por el dueño en la consulta a la base.
- La cátedra identifica todo por el `groupId` de la cuenta técnica, que es la misma para todos
  los usuarios. La propiedad por usuario la aplica turnos.

## Secretos

- El JWT técnico de la cátedra, la contraseña de Redis, el secreto de los JWT y las
  contraseñas de las bases van solo en variables de entorno: `.env` en cada servicio y
  `docker/.env` para Docker. Los tres están en `.gitignore`. Los `.env.example` tienen solo
  los nombres.
- La app Android nunca recibe credenciales de la cátedra: habla solo con nuestros servicios.
- No se escriben secretos ni teléfonos en los logs. Los mensajes de error de la cátedra se
  registran sin el token.
- En Docker no se usa el perfil `secret-samples` de JHipster, que trae un secreto de ejemplo.

## Validación de datos

En cada límite se valida lo que entra:

- Los bodies de la API usan Bean Validation (`@NotBlank`, `@Size`) con los mismos límites
  que la cátedra.
- El teléfono se valida con la misma regla que la cátedra antes de mandarlo.
- Las fechas pasadas, los turnos fuera de agenda, los estados inválidos y los parámetros
  de paginación fuera de rango dan 400 o 409 con un `code` estable.
- Los mensajes de Kafka que no se pueden leer se descartan, y los campos desconocidos se
  ignoran.

## CORS

No hay CORS configurado en ningún perfil. CORS es un mecanismo de los navegadores, y el único
cliente del sistema es la app Android, que no es un navegador. Sin configuración, un sitio web
ajeno no puede llamar a la API desde el navegador de un usuario. JHipster traía orígenes de
ejemplo en el perfil de desarrollo (Ionic y GitHub Codespaces), y se sacaron.

## HTTPS

Limitación del entorno local: los servicios atienden HTTP, publicado solo en `127.0.0.1`.
El emulador de Android llega a la máquina por `10.0.2.2` sin salir a la red. Los
contenedores hablan con la cátedra por ZeroTier, que ya cifra el tráfico entre nodos.

En un despliegue real habría que poner delante un proxy con TLS (por ejemplo, nginx con un
certificado) y publicar solo el 443. JHipster trae además un perfil `tls` para servir HTTPS
directamente.

## Rol administrador

No se implementó el rol administrador opcional (sección 9). El rol `ADMIN` de JHipster
existe, pero en producción ningún usuario activo lo tiene.
