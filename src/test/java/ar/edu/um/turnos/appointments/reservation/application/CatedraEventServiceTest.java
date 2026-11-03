package ar.edu.um.turnos.appointments.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.turnos.appointments.reservation.domain.Appointment;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.EventResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CatedraEventServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneOffset.UTC);
    private static final Instant VENCE = Instant.parse("2026-11-02T13:15:00Z");
    private static final Instant VENCE_DESPUES = Instant.parse("2026-11-02T13:20:00Z");

    private InMemoryReservationProcesses procesos;
    private CatedraEventService service;
    private long id;

    @BeforeEach
    void unProcesoConfirmadoInicialmente() {
        procesos = new InMemoryReservationProcesses();
        service = new CatedraEventService(procesos, RELOJ);
        Appointment turno = new Appointment(
            1051,
            15,
            "Ana Torres",
            "Cardiologia",
            LocalDate.of(2026, 11, 9),
            LocalTime.of(9, 0),
            LocalTime.of(9, 30)
        );
        ReservationProcess proceso = procesos.create(
            ReservationProcess.held(7, turno, new Patient("user-7", "Juan", "Perez"), "hold-1", "process-1", VENCE, RELOJ.instant())
        );
        id = proceso.id();
        procesos.update(id, p -> p.initialConfirmationAccepted(VENCE, RELOJ.instant()));
    }

    @Test
    void elPedidoDeTelefonoGuardaSuEventId() {
        EventResult resultado = service.handle(evento("request-1", CatedraEvent.Type.PHONE_REQUESTED));

        assertThat(resultado).isEqualTo(EventResult.APPLIED);
        assertThat(procesos.find(id).requestEventId()).isEqualTo("request-1");
        assertThat(procesos.find(id).expiresAt()).isEqualTo(VENCE_DESPUES);
    }

    @Test
    void laConfirmacionGuardaElIdDeLaReserva() {
        service.handle(evento("request-1", CatedraEvent.Type.PHONE_REQUESTED));
        service.handle(evento("confirmation-1", CatedraEvent.Type.CONFIRMED));

        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(procesos.find(id).reservationId()).isEqualTo(8251L);
    }

    @Test
    void unEventoRepetidoNoSeAplicaDosVeces() {
        service.handle(evento("request-1", CatedraEvent.Type.PHONE_REQUESTED));
        procesos.update(id, p -> p.phoneSubmitted("+5492615551234", RELOJ.instant()));
        service.handle(evento("rejection-1", CatedraEvent.Type.PHONE_REJECTED));
        procesos.update(id, p -> p.phoneSubmitted("+5492615559999", RELOJ.instant()));

        // El rechazo viejo llega otra vez: no tiene que volver a pedir el teléfono
        EventResult resultado = service.handle(evento("rejection-1", CatedraEvent.Type.PHONE_REJECTED));

        assertThat(resultado).isEqualTo(EventResult.DUPLICATE);
        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
    }

    @Test
    void unVencimientoTardioNoDeshaceLaConfirmacion() {
        service.handle(evento("confirmation-1", CatedraEvent.Type.CONFIRMED));

        EventResult resultado = service.handle(evento("expired-1", CatedraEvent.Type.EXPIRED));

        // El evento se registra, pero el dominio no lo aplica: la reserva sigue confirmada
        assertThat(resultado).isEqualTo(EventResult.APPLIED);
        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void elVencimientoCierraElProceso() {
        service.handle(evento("request-1", CatedraEvent.Type.PHONE_REQUESTED));
        service.handle(evento("expired-1", CatedraEvent.Type.EXPIRED));

        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(procesos.find(id).statusReason()).isEqualTo("HOLD_EXPIRED");
    }

    @Test
    void unProcesoInvalidoSeCierra() {
        service.handle(evento("invalid-1", CatedraEvent.Type.INVALID));

        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.INVALID);
        assertThat(procesos.find(id).statusReason()).isEqualTo("REQUEST_EVENT_MISMATCH");
    }

    @Test
    void laCancelacionLlegaDespuesDeConfirmar() {
        service.handle(evento("confirmation-1", CatedraEvent.Type.CONFIRMED));
        service.handle(evento("cancellation-1", CatedraEvent.Type.CANCELLED));

        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void unEventoDeOtroProcesoSeInforma() {
        CatedraEvent ajeno = new CatedraEvent("x-1", CatedraEvent.Type.CONFIRMED, "process-de-otro", 1L, null, null, null);

        assertThat(service.handle(ajeno)).isEqualTo(EventResult.UNKNOWN_PROCESS);
    }

    private static CatedraEvent evento(String eventId, CatedraEvent.Type tipo) {
        return switch (tipo) {
            case PHONE_REQUESTED -> new CatedraEvent(eventId, tipo, "process-1", null, VENCE_DESPUES, null, null);
            case PHONE_REJECTED -> new CatedraEvent(
                eventId,
                tipo,
                "process-1",
                null,
                VENCE_DESPUES,
                "INVALID_PHONE_NUMBER",
                "El telefono ingresado no es valido."
            );
            case CONFIRMED -> new CatedraEvent(eventId, tipo, "process-1", 8251L, null, null, null);
            case EXPIRED -> new CatedraEvent(eventId, tipo, "process-1", null, null, "HOLD_EXPIRED", null);
            case INVALID -> new CatedraEvent(eventId, tipo, "process-1", null, null, "REQUEST_EVENT_MISMATCH", null);
            case CANCELLED -> new CatedraEvent(eventId, tipo, "process-1", 8251L, null, "Cambio solicitado por el usuario", null);
        };
    }
}
