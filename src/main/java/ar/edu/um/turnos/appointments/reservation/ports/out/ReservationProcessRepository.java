package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
     * Procesos de un usuario, del turno más lejano al más cercano.
     *
     * @param statuses estados a incluir; vacío para todos.
     */
    ReservationPage findByOwner(long ownerId, Set<ReservationStatus> statuses, int page, int size);

    /**
     * Procesos abiertos (HELD, WAITING_FOR_PHONE o PHONE_SUBMITTED) que vencieron antes de
     * {@code limit}, los más viejos primero.
     */
    List<ReservationProcess> findOpenExpiredBefore(Instant limit, int max);

    /**
     * Lee el proceso, le aplica el cambio y lo guarda, todo en una transacción y con el registro
     * bloqueado. Así una respuesta REST y un evento Kafka que llegan juntos no se pisen.
     */
    ReservationProcess update(long id, Consumer<ReservationProcess> change);

    /**
     * Aplica un evento de Kafka una sola vez. En la misma transacción se bloquea el proceso, se
     * aplica el cambio y se guarda el eventId: o queda todo o no queda nada. Si el evento llega de
     * nuevo, se reconoce por el eventId y no se aplica otra vez.
     */
    EventResult applyOnce(String reservationProcessId, String eventId, Consumer<ReservationProcess> change);

    record ReservationPage(List<ReservationProcess> items, long total) {}

    enum EventResult {
        APPLIED,
        /** El eventId ya se había procesado. */
        DUPLICATE,
        /** No hay ningún proceso con ese reservationProcessId. */
        UNKNOWN_PROCESS,
    }
}
