package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * El usuario pidió algo que el proceso no permite en su estado actual.
 */
public class InvalidReservationStateException extends RuntimeException {

    public InvalidReservationStateException(String message) {
        super(message);
    }
}
