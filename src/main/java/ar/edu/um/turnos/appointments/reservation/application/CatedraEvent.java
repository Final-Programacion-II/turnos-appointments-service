package ar.edu.um.turnos.appointments.reservation.application;

import java.time.Instant;

/**
 * Un evento de la cátedra sobre un proceso de reserva, ya traducido del JSON de Kafka.
 * Cada tipo usa solo algunos campos; los demás llegan en null.
 */
public record CatedraEvent(
    String eventId,
    Type type,
    String reservationProcessId,
    Long reservationId,
    Instant expiresAt,
    String reason,
    String message
) {
    public enum Type {
        /** AdditionalInformationRequested */
        PHONE_REQUESTED,
        /** AdditionalInformationRejected */
        PHONE_REJECTED,
        /** AppointmentConfirmed */
        CONFIRMED,
        /** AppointmentProcessExpired */
        EXPIRED,
        /** AppointmentProcessInvalid */
        INVALID,
        /** AppointmentCancelled */
        CANCELLED,
    }
}
