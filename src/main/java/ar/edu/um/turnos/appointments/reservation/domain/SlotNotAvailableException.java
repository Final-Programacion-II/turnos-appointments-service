package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * El turno pedido no está entre los libres: no es de la agenda, ya pasó o está ocupado.
 */
public class SlotNotAvailableException extends RuntimeException {

    public SlotNotAvailableException(LocalDate date, LocalTime startTime) {
        super("El turno del " + date + " a las " + startTime + " no está disponible");
    }
}
