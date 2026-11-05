package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * Una reserva como la informa la cátedra en GET /api/appointments (sección 11 de la referencia).
 * Es la fuente de verdad cuando falta el resultado por Kafka.
 */
public record CatedraAppointment(String reservationProcessId, Status status, Long reservationId, String cancellationReason) {
    public enum Status {
        /** Esperando el teléfono. */
        PHONE_PENDING,
        CONFIRMED,
        CANCELLED,
        /** No se completó: venció o fue inválido. */
        FAILED,
    }
}
