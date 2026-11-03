package ar.edu.um.turnos.appointments.reservation.infrastructure.persistence;

import ar.edu.um.turnos.appointments.reservation.domain.Appointment;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda los procesos de reserva en PostgreSQL.
 */
@Component
public class JpaReservationProcessRepository implements ReservationProcessRepository {

    private final ReservationProcessJpaRepository jpa;

    public JpaReservationProcessRepository(ReservationProcessJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public ReservationProcess create(ReservationProcess process) {
        ReservationProcessEntity entity = new ReservationProcessEntity();
        entity.ownerId = process.ownerId();
        entity.professionalId = process.appointment().professionalId();
        entity.professionalExternalId = process.appointment().professionalExternalId();
        entity.professionalName = process.appointment().professionalName();
        entity.categoryName = process.appointment().categoryName();
        entity.appointmentDate = process.appointment().date();
        entity.startTime = process.appointment().startTime();
        entity.endTime = process.appointment().endTime();
        entity.externalPatientId = process.patient().externalPatientId();
        entity.patientFirstName = process.patient().firstName();
        entity.patientLastName = process.patient().lastName();
        entity.holdId = process.holdId();
        entity.reservationProcessId = process.reservationProcessId();
        entity.createdAt = process.createdAt();
        copyChangingFields(process, entity);
        return toDomain(jpa.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReservationProcess> findById(long id) {
        return jpa.findById(id).map(JpaReservationProcessRepository::toDomain);
    }

    @Override
    @Transactional
    public ReservationProcess update(long id, Consumer<ReservationProcess> change) {
        ReservationProcessEntity entity = jpa.findByIdForUpdate(id).orElseThrow(() -> new ReservationNotFoundException(id));
        ReservationProcess process = toDomain(entity);
        change.accept(process);
        // Hibernate guarda los cambios de la entidad al terminar la transacción
        copyChangingFields(process, entity);
        return process;
    }

    /**
     * Lo que puede cambiar durante el proceso. El resto se escribe una sola vez, al crearlo.
     */
    private static void copyChangingFields(ReservationProcess process, ReservationProcessEntity entity) {
        entity.status = process.status();
        entity.expiresAt = process.expiresAt();
        entity.requestEventId = process.requestEventId();
        entity.phoneNumber = process.phoneNumber();
        entity.reservationId = process.reservationId();
        entity.statusReason = process.statusReason();
        entity.updatedAt = process.updatedAt();
    }

    private static ReservationProcess toDomain(ReservationProcessEntity e) {
        return new ReservationProcess(
            e.id,
            e.ownerId,
            new Appointment(
                e.professionalId,
                e.professionalExternalId,
                e.professionalName,
                e.categoryName,
                e.appointmentDate,
                e.startTime,
                e.endTime
            ),
            new Patient(e.externalPatientId, e.patientFirstName, e.patientLastName),
            e.holdId,
            e.reservationProcessId,
            e.status,
            e.expiresAt,
            e.requestEventId,
            e.phoneNumber,
            e.reservationId,
            e.statusReason,
            e.createdAt,
            e.updatedAt
        );
    }
}
