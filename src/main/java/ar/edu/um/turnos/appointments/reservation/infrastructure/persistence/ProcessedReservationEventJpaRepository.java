package ar.edu.um.turnos.appointments.reservation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedReservationEventJpaRepository extends JpaRepository<ProcessedReservationEventEntity, String> {}
