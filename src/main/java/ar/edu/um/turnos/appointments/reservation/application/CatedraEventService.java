package ar.edu.um.turnos.appointments.reservation.application;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.EventResult;
import java.time.Clock;
import java.time.Instant;

/**
 * Aplica a los procesos de reserva los eventos que la cátedra publica por Kafka.
 *
 * Kafka puede entregar un evento más de una vez y en cualquier momento. Por eso:
 * - el repositorio aplica cada eventId una sola vez (applyOnce);
 * - el dominio ignora los eventos que no corresponden al estado actual, así un evento viejo
 *   no reabre un proceso terminado.
 */
public class CatedraEventService {

    private final ReservationProcessRepository processes;
    private final Clock clock;

    public CatedraEventService(ReservationProcessRepository processes, Clock clock) {
        this.processes = processes;
        this.clock = clock;
    }

    public EventResult handle(CatedraEvent event) {
        Instant now = clock.instant();
        return processes.applyOnce(event.reservationProcessId(), event.eventId(), process -> apply(process, event, now));
    }

    private static void apply(ReservationProcess process, CatedraEvent event, Instant now) {
        switch (event.type()) {
            // El eventId del pedido se guarda: hay que devolverlo como requestEventId junto con el teléfono
            case PHONE_REQUESTED -> process.phoneRequested(event.eventId(), event.expiresAt(), now);
            case PHONE_REJECTED -> process.phoneRejected(event.message(), event.expiresAt(), now);
            case CONFIRMED -> process.confirmed(event.reservationId(), now);
            case EXPIRED -> process.expired(event.reason(), now);
            case INVALID -> process.invalid(event.reason(), now);
            case CANCELLED -> process.cancelled(event.reason(), now);
        }
    }
}
