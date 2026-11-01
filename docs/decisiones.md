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
