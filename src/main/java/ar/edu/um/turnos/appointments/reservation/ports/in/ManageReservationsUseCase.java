package ar.edu.um.turnos.appointments.reservation.ports.in;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.ReservationPage;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/**
 * Lo que un usuario puede hacer con sus reservas. Todas las operaciones reciben el id del
 * usuario autenticado: uno nunca ve ni toca procesos de otro.
 */
public interface ManageReservationsUseCase {
    /**
     * Empieza una reserva: pide el hold y lo confirma inicialmente.
     */
    ReservationProcess start(long ownerId, StartReservation request);

    /**
     * Reintenta la confirmación inicial de un proceso que quedó HELD.
     */
    ReservationProcess retryConfirmation(long ownerId, long id);

    /**
     * Manda a la cátedra el teléfono que pidió.
     */
    ReservationProcess submitPhone(long ownerId, long id, String rawPhone);

    /**
     * @param statuses estados a incluir; vacío para todos.
     */
    ReservationPage list(long ownerId, Set<ReservationStatus> statuses, int page, int size);

    /**
     * Cancela una reserva confirmada.
     */
    ReservationProcess cancel(long ownerId, long id, String reason);

    ReservationProcess get(long ownerId, long id);

    /**
     * Lo que pide el usuario. professionalId es el id local del catálogo.
     */
    record StartReservation(long professionalId, LocalDate date, LocalTime startTime, String patientFirstName, String patientLastName) {}
}
