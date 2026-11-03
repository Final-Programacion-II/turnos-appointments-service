package ar.edu.um.turnos.appointments.reservation.ports.out;

import java.time.Instant;

/**
 * Manda a la cátedra el teléfono que pidió (AdditionalInformationSubmitted por Kafka).
 * Si no se puede confirmar el envío, lanza CatedraUnavailableException.
 */
public interface PhoneSubmissionPublisher {
    void publish(PhoneSubmission submission);

    /**
     * @param eventId        id nuevo para cada envío (la cátedra deduplica por este id).
     * @param requestEventId el eventId del AdditionalInformationRequested que se responde.
     */
    record PhoneSubmission(
        String eventId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        String phoneNumber,
        Instant occurredAt
    ) {}
}
