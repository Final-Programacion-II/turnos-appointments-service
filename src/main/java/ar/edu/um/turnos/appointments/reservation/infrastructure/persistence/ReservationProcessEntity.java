package ar.edu.um.turnos.appointments.reservation.infrastructure.persistence;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Tabla reservation_process. Es solo el formato de guardado: las reglas están en
 * ReservationProcess (dominio), y el adaptador copia los datos de uno al otro.
 */
@Entity
@Table(name = "reservation_process")
public class ReservationProcessEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    Long ownerId;

    @Column(name = "professional_id", nullable = false, updatable = false)
    Long professionalId;

    @Column(name = "professional_external_id", nullable = false, updatable = false)
    Long professionalExternalId;

    @Column(name = "professional_name", nullable = false, updatable = false)
    String professionalName;

    @Column(name = "category_name", updatable = false)
    String categoryName;

    @Column(name = "appointment_date", nullable = false, updatable = false)
    LocalDate appointmentDate;

    // Hora de reloj del consultorio: sin esto Hibernate la convierte a UTC (hibernate.jdbc.time_zone)
    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(name = "start_time", nullable = false, updatable = false)
    LocalTime startTime;

    @JdbcTypeCode(SqlTypes.LOCAL_TIME)
    @Column(name = "end_time", nullable = false, updatable = false)
    LocalTime endTime;

    @Column(name = "external_patient_id", nullable = false, updatable = false)
    String externalPatientId;

    @Column(name = "patient_first_name", nullable = false, updatable = false)
    String patientFirstName;

    @Column(name = "patient_last_name", nullable = false, updatable = false)
    String patientLastName;

    @Column(name = "hold_id", nullable = false, updatable = false)
    String holdId;

    @Column(name = "reservation_process_id", nullable = false, updatable = false)
    String reservationProcessId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    ReservationStatus status;

    @Column(name = "expires_at")
    Instant expiresAt;

    @Column(name = "request_event_id")
    String requestEventId;

    @Column(name = "phone_number")
    String phoneNumber;

    @Column(name = "reservation_id")
    Long reservationId;

    @Column(name = "status_reason")
    String statusReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
