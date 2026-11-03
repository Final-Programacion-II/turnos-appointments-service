package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dónde se guardan los procesos de reserva.
 */
public interface ReservationProcessRepository {
    /**
     * Guarda un proceso nuevo y lo devuelve con su id.
     */
    ReservationProcess create(ReservationProcess process);

    Optional<ReservationProcess> findById(long id);

    /**
     * Lee el proceso, le aplica el cambio y lo guarda, todo en una transacción y con el registro
     * bloqueado. Así una respuesta REST y un evento Kafka que llegan juntos no se pisan.
     */
    ReservationProcess update(long id, Consumer<ReservationProcess> change);
}
