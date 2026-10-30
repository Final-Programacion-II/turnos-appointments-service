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

## 5. Stack

- JHipster 9.2.0 con Spring Boot 4, Java 21 y Maven Wrapper (`./mvnw`).
- PostgreSQL, con la versión definida por la configuración Docker de JHipster.
- Node 22 LTS, usado solo por las herramientas de JHipster.
- Kafka con `spring-kafka`; Redis con Lettuce.
- App: Kotlin Multiplatform, con librerías a definir antes de empezar la app.
- Secretos (JWT técnico, credenciales de Redis y Kafka) solo en variables de entorno,
  nunca en el repositorio.
