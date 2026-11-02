package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * La reserva no existe o es de otro usuario. Los dos casos dan el mismo error para no
 * revelar qué ids existen.
 */
public class ReservationNotFoundException extends RuntimeException {

    public ReservationNotFoundException(long id) {
        super("No existe la reserva " + id);
    }
}
