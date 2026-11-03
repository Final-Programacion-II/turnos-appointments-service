package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * Estados locales de un proceso de reserva. Los tres primeros están "abiertos": el proceso
 * todavía puede avanzar. Los demás son resultados: un evento tardío no los cambia.
 */
public enum ReservationStatus {
    /** La cátedra retiene el turno (hold). Falta la confirmación inicial. */
    HELD,
    /** La cátedra aceptó la confirmación inicial y va a pedir el teléfono por Kafka. */
    WAITING_FOR_PHONE,
    /** Se mandó el teléfono. Falta la respuesta de la cátedra. */
    PHONE_SUBMITTED,
    /** Turno reservado. Es el único resultado que todavía se puede cancelar. */
    CONFIRMED,
    CANCELLED,
    /** Venció el hold o el proceso antes de completarse. */
    EXPIRED,
    /** La cátedra detectó una inconsistencia en el proceso. */
    INVALID,
    /** La cátedra rechazó la confirmación inicial (por ejemplo, el hold no existe). */
    FAILED;

    public boolean isOpen() {
        return this == HELD || this == WAITING_FOR_PHONE || this == PHONE_SUBMITTED;
    }
}
