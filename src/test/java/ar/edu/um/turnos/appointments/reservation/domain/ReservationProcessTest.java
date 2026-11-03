package ar.edu.um.turnos.appointments.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class ReservationProcessTest {

    private static final Instant AHORA = Instant.parse("2026-11-02T13:00:00Z");
    private static final Instant VENCE = Instant.parse("2026-11-02T13:15:00Z");
    private static final Instant VENCE_DESPUES = Instant.parse("2026-11-02T13:20:00Z");

    @Test
    void empiezaRetenido() {
        ReservationProcess proceso = nuevo();

        assertThat(proceso.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(proceso.expiresAt()).isEqualTo(VENCE);
        assertThat(proceso.createdAt()).isEqualTo(AHORA);
    }

    @Test
    void caminoCompletoHastaConfirmar() {
        ReservationProcess proceso = nuevo();

        assertThat(proceso.initialConfirmationAccepted(VENCE_DESPUES, AHORA)).isTrue();
        assertThat(proceso.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(proceso.expiresAt()).isEqualTo(VENCE_DESPUES);

        assertThat(proceso.phoneRequested("request-1", VENCE_DESPUES, AHORA)).isTrue();
        proceso.phoneSubmitted("+5492615551234", AHORA);
        assertThat(proceso.status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);

        assertThat(proceso.confirmed(8251L, AHORA)).isTrue();
        assertThat(proceso.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(proceso.reservationId()).isEqualTo(8251);
    }

    @Test
    void elPedidoDeTelefonoPuedeLlegarAntesQueLaRespuestaRest() {
        ReservationProcess proceso = nuevo();

        // Kafka gana la carrera
        assertThat(proceso.phoneRequested("request-1", VENCE, AHORA)).isTrue();
        // La respuesta REST llega después y no cambia nada
        assertThat(proceso.initialConfirmationAccepted(VENCE, AHORA)).isFalse();

        assertThat(proceso.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(proceso.requestEventId()).isEqualTo("request-1");
    }

    @Test
    void unTelefonoRechazadoSePuedeVolverAMandar() {
        ReservationProcess proceso = esperandoTelefono();
        proceso.phoneSubmitted("123", AHORA);

        assertThat(proceso.phoneRejected("El telefono ingresado no es valido.", VENCE_DESPUES, AHORA)).isTrue();

        assertThat(proceso.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(proceso.statusReason()).isEqualTo("El telefono ingresado no es valido.");
        proceso.phoneSubmitted("+5492615551234", AHORA);
        assertThat(proceso.status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
    }

    @Test
    void noSeMandaElTelefonoSiLaCatedraNoLoPidio() {
        ReservationProcess proceso = nuevo();
        proceso.initialConfirmationAccepted(VENCE, AHORA);

        // Falta el AdditionalInformationRequested (y su eventId, que hay que devolver)
        assertThatThrownBy(() -> proceso.ensurePhoneCanBeSubmitted(AHORA)).isInstanceOf(InvalidReservationStateException.class);
    }

    @Test
    void noSeMandaElTelefonoSiElProcesoVencio() {
        ReservationProcess proceso = esperandoTelefono();

        assertThatThrownBy(() -> proceso.ensurePhoneCanBeSubmitted(VENCE))
            .isInstanceOf(InvalidReservationStateException.class)
            .hasMessageContaining("venció");
    }

    @Test
    void siElResultadoLlegaAntesQueElTelefonoNoSeRetrocede() {
        ReservationProcess proceso = esperandoTelefono();
        proceso.ensurePhoneCanBeSubmitted(AHORA);
        // Mientras se publicaba el teléfono, llegó la confirmación por Kafka
        proceso.confirmed(8251L, AHORA);

        assertThat(proceso.phoneSubmitted("+5492615551234", AHORA)).isFalse();
        assertThat(proceso.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void unResultadoNoSeReabre() {
        ReservationProcess proceso = esperandoTelefono();
        proceso.expired("HOLD_EXPIRED", AHORA);

        // Avisos tardíos o repetidos: no cambian nada
        assertThat(proceso.confirmed(8251L, AHORA)).isFalse();
        assertThat(proceso.phoneRequested("request-2", VENCE_DESPUES, AHORA)).isFalse();
        assertThat(proceso.initialConfirmationAccepted(VENCE_DESPUES, AHORA)).isFalse();
        assertThat(proceso.invalid("REQUEST_EVENT_MISMATCH", AHORA)).isFalse();

        assertThat(proceso.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(proceso.statusReason()).isEqualTo("HOLD_EXPIRED");
        assertThat(proceso.reservationId()).isNull();
    }

    @Test
    void unaConfirmadaNoVenceNiSeInvalida() {
        ReservationProcess proceso = esperandoTelefono();
        proceso.confirmed(8251L, AHORA);

        assertThat(proceso.expired("HOLD_EXPIRED", AHORA)).isFalse();
        assertThat(proceso.invalid("REQUEST_EVENT_MISMATCH", AHORA)).isFalse();
        assertThat(proceso.confirmed(8251L, AHORA)).isFalse();

        assertThat(proceso.status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void soloSeCancelaUnaConfirmada() {
        ReservationProcess abierta = esperandoTelefono();
        assertThat(abierta.cancelled("Cambio de planes", AHORA)).isFalse();
        assertThat(abierta.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);

        ReservationProcess confirmada = esperandoTelefono();
        confirmada.confirmed(8251L, AHORA);
        assertThat(confirmada.cancelled("Cambio de planes", AHORA)).isTrue();
        assertThat(confirmada.status()).isEqualTo(ReservationStatus.CANCELLED);

        // Cancelar dos veces es lo mismo que una
        assertThat(confirmada.cancelled("Otra vez", AHORA)).isFalse();
        assertThat(confirmada.statusReason()).isEqualTo("Cambio de planes");
    }

    @Test
    void elMotivoSeRecortaAlLargoDeLaColumna() {
        ReservationProcess proceso = nuevo();

        proceso.failed("x".repeat(800), AHORA);

        assertThat(proceso.statusReason()).hasSize(500);
    }

    @Test
    void elDuenioEsElQueLaEmpezo() {
        ReservationProcess proceso = nuevo();

        assertThat(proceso.belongsTo(7)).isTrue();
        assertThat(proceso.belongsTo(8)).isFalse();
    }

    private static ReservationProcess esperandoTelefono() {
        ReservationProcess proceso = nuevo();
        proceso.phoneRequested("request-1", VENCE, AHORA);
        return proceso;
    }

    private static ReservationProcess nuevo() {
        Appointment turno = new Appointment(
            1051,
            15,
            "Ana Torres",
            "Cardiologia",
            LocalDate.of(2026, 11, 9),
            LocalTime.of(9, 0),
            LocalTime.of(9, 30)
        );
        return ReservationProcess.held(7, turno, new Patient("user-7", "Juan", "Perez"), "hold-1", "process-1", VENCE, AHORA);
    }
}
