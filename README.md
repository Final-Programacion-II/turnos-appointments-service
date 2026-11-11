<!-- calidad:inicio -->
![Calidad](https://img.shields.io/badge/Calidad-60%2F100-green) ![Aprobar](https://img.shields.io/badge/Aprobar-no-red) ![Destacar](https://img.shields.io/badge/Destacar-no-lightgrey) ![Build](https://img.shields.io/badge/Build-OK-green) ![Duplicación](https://img.shields.io/badge/Duplicaci%C3%B3n-9.2%25-orange) ![%POO](https://img.shields.io/badge/%25POO-83%25-green) ![Sueltas](https://img.shields.io/badge/Sueltas-0-green) ![D1](https://img.shields.io/badge/D1-1-red) ![D2](https://img.shields.io/badge/D2-0-red) ![D4](https://img.shields.io/badge/D4-90-green) ![D5](https://img.shields.io/badge/D5-100-green) ![D6](https://img.shields.io/badge/D6-100-green) ![D7](https://img.shields.io/badge/D7-100-green)

**Calidad (heurístico, 7 dimensiones):** índice **60/100** · build **OK** · aprobar **no (5/11)** · destacar **no**
Detalle completo en [`docs/EVALUACION.md`](docs/EVALUACION.md).
<!-- calidad:fin -->

# turnos-appointments-service

Servicio de turnos y reservas del sistema distribuido de turnos (Proyecto integrador 2026,
Programación II, UM). Gestiona los usuarios de la app, calcula los turnos disponibles y
coordina con el servicio central de la cátedra el proceso de reserva, por REST y Kafka.

El sistema tiene tres repositorios:

| Repo                                                                                      | Qué hace                                                                                   |
| ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| **turnos-appointments-service** (este)                                                    | Usuarios, disponibilidad y reservas. Tiene el Docker Compose de todo el sistema.           |
| [turnos-catalog-service](https://github.com/Final-Programacion-II/turnos-catalog-service) | Copia local del catálogo de la cátedra (categorías, profesionales y horarios) y búsquedas. |
| [turnos-kmp-app](https://github.com/Final-Programacion-II/turnos-kmp-app)                 | App Android con Kotlin Multiplatform.                                                      |

La arquitectura completa está en [docs/arquitectura.md](docs/arquitectura.md).

## Requisitos

- Java 21 y Docker (para PostgreSQL y para los tests con Testcontainers).
- ZeroTier conectado a la red de la cátedra, con la cuenta de integración ya registrada.
- El servicio de catálogo corriendo en `http://localhost:8081`: sin él no se puede calcular la
  disponibilidad ni reservar.

## Configuración

Las URLs y los secretos van en variables de entorno, nunca en el repositorio:

```bash
cp .env.example .env
chmod 600 .env
```

Hay que completar el `.env` con los datos de la cuenta de integración de la cátedra (API, JWT
técnico, groupId y topics de Kafka) y con el secreto de los JWT. Ese secreto tiene que ser
el mismo que el del catálogo. Cada variable está explicada en `.env.example`.

## Levantar en desarrollo

```bash
set -a; source .env; set +a
./mvnw
```

Queda en `http://localhost:8082`. Spring levanta solo el PostgreSQL de desarrollo con Docker
(`src/main/docker/services.yml`).

## Levantar el sistema completo con Docker

Los dos servicios y sus bases, con un solo comando. Los pasos (construir las imágenes, armar
`docker/.env` y levantar) están en [docs/docker.md](docs/docker.md):

```bash
docker compose -f docker/compose.yml up -d
```

## Tests

Los tests de integración usan Testcontainers, así que necesitan Docker.

```bash
./mvnw verify                                  # todos: unitarios e integración
./mvnw test                                    # solo los unitarios
./mvnw test -Dtest=ReservationServiceTest      # una clase de tests unitarios
./mvnw verify -Dit.test=ReservationIT          # una clase de tests de integración
```

El reporte de cobertura queda en `target/site/jacoco/index.html` (unitarios) y en
`target/site/jacoco-it/index.html` (integración). El CI de GitHub corre `./mvnw verify` en
cada PR (`.github/workflows/ci.yml`).

Qué cubren los tests de cada parte:

| Parte                         | Tests                                                                                                         |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------- |
| Registro, login y contraseñas | `AccountResourceIT`, `AuthenticateControllerIT`, `UserServiceIT`, `DomainUserDetailsServiceIT`                |
| Disponibilidad                | `AvailabilityTest`, `AvailabilityIT`, `CatalogRestAdapterTest`, `CatedraRestAdapterTest`                      |
| Proceso de reserva y estados  | `ReservationProcessTest`, `ReservationServiceTest`, `CatedraHoldAdapterTest`, `ReservationIT`                 |
| Kafka e idempotencia          | `CatedraEventServiceTest`, `CatedraActionsListenerTest`, `KafkaPhoneSubmissionPublisherTest`, `ReservationIT` |
| Reconciliación                | `ReconciliationServiceTest`, `CatedraAppointmentsAdapterTest`, `ReservationIT`                                |
| Autorización entre usuarios   | `ReservationServiceTest`, `ReservationIT`                                                                     |
| Arquitectura                  | `ReservationArchitectureTest` (el dominio no depende de Spring, JPA ni Jackson)                               |

## Documentación

| Documento                               | Contenido                                                          |
| --------------------------------------- | ------------------------------------------------------------------ |
| [arquitectura.md](docs/arquitectura.md) | Componentes, integraciones con la cátedra y propiedad de los datos |
| [api.md](docs/api.md)                   | API para la app, contrato con el catálogo y formato de errores     |
| [modelo-datos.md](docs/modelo-datos.md) | Tablas del servicio y qué datos son suyos                          |
| [reservas.md](docs/reservas.md)         | Proceso de reserva, estados, idempotencia y recuperación           |
| [seguridad.md](docs/seguridad.md)       | Autenticación, autorización, secretos y limitaciones               |
| [docker.md](docs/docker.md)             | Levantar el sistema con Docker Compose                             |
| [decisiones.md](docs/decisiones.md)     | Decisiones técnicas, con motivos y alternativas descartadas        |
| [catedra/](docs/catedra/)               | Enunciado y referencia de integración de la cátedra                |

La sincronización del catálogo está documentada en el repo del catálogo
([sincronizacion.md](https://github.com/Final-Programacion-II/turnos-catalog-service/blob/main/docs/sincronizacion.md)).
