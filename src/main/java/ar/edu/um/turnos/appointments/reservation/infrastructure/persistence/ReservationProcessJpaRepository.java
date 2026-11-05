package ar.edu.um.turnos.appointments.reservation.infrastructure.persistence;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationProcessJpaRepository extends JpaRepository<ReservationProcessEntity, Long> {
    /**
     * SELECT ... FOR UPDATE: otra transacción que quiera cambiar el mismo proceso espera a que
     * esta termine.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ReservationProcessEntity p where p.id = :id")
    Optional<ReservationProcessEntity> findByIdForUpdate(@Param("id") Long id);

    /**
     * Igual, pero buscando por el id que usa la cátedra en los eventos de Kafka.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ReservationProcessEntity p where p.reservationProcessId = :processId")
    Optional<ReservationProcessEntity> findByReservationProcessIdForUpdate(@Param("processId") String processId);

    /**
     * Procesos en alguno de los estados dados que vencieron antes de {@code limit}.
     */
    List<ReservationProcessEntity> findByStatusInAndExpiresAtBeforeOrderByExpiresAtAsc(
        Collection<ReservationStatus> statuses,
        Instant limit,
        Pageable pageable
    );

    Page<ReservationProcessEntity> findByOwnerId(Long ownerId, Pageable pageable);

    Page<ReservationProcessEntity> findByOwnerIdAndStatusIn(Long ownerId, Collection<ReservationStatus> statuses, Pageable pageable);
}
