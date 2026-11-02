package ar.edu.um.turnos.appointments.reservation.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
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
}
