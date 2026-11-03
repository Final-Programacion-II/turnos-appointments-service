package ar.edu.um.turnos.appointments.reservation.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Un evento de Kafka que ya se aplicó. El eventId es la clave primaria: el mismo evento no
 * puede quedar registrado dos veces.
 */
@Entity
@Table(name = "processed_reservation_event")
public class ProcessedReservationEventEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    String eventId;

    @Column(name = "reservation_process_id", nullable = false, updatable = false)
    String reservationProcessId;

    @Column(name = "processed_at", nullable = false, updatable = false)
    Instant processedAt;

    protected ProcessedReservationEventEntity() {}

    ProcessedReservationEventEntity(String eventId, String reservationProcessId, Instant processedAt) {
        this.eventId = eventId;
        this.reservationProcessId = reservationProcessId;
        this.processedAt = processedAt;
    }
}
