package ar.edu.um.turnos.appointments.reservation.application;

import ar.edu.um.turnos.appointments.reservation.domain.Appointment;
import ar.edu.um.turnos.appointments.reservation.domain.AvailableSlot;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.domain.SlotNotAvailableException;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Empieza una reserva: valida el turno, pide el hold a la cátedra, lo guarda y hace la
 * confirmación inicial. El resto del proceso (teléfono y resultado) llega por Kafka.
 *
 * Las llamadas a la cátedra se hacen fuera de cualquier transacción de la base: una llamada
 * lenta no deja registros bloqueados.
 */
public class ReservationService {

    private final AvailabilityService availability;
    private final CatedraReservations catedra;
    private final ReservationProcessRepository processes;
    private final Clock clock;

    public ReservationService(
        AvailabilityService availability,
        CatedraReservations catedra,
        ReservationProcessRepository processes,
        Clock clock
    ) {
        this.availability = availability;
        this.catedra = catedra;
        this.processes = processes;
        this.clock = clock;
    }

    /**
     * Si la cátedra rechaza el hold (por ejemplo, otro lo tomó recién), se lanza la excepción y
     * no se guarda nada. Si el hold sale bien pero la confirmación no llega a la cátedra, el
     * proceso queda HELD y el usuario puede reintentar con {@link #retryConfirmation}.
     */
    public ReservationProcess start(long ownerId, StartReservation request) {
        // Siempre con la agenda vigente del catálogo y las ocupaciones actuales de la cátedra
        AvailabilityService.Result result = availability.availability(request.professionalId(), request.date());
        AvailableSlot slot = result
            .slots()
            .stream()
            .filter(s -> s.startTime().equals(request.startTime()))
            .findFirst()
            .orElseThrow(() -> new SlotNotAvailableException(request.date(), request.startTime()));

        ProfessionalAgenda agenda = result.agenda();
        Appointment appointment = new Appointment(
            agenda.professionalId(),
            agenda.externalId(),
            agenda.firstName() + " " + agenda.lastName(),
            agenda.categoryName(),
            request.date(),
            slot.startTime(),
            slot.endTime()
        );
        Patient patient = new Patient(Patient.externalIdOf(ownerId), request.patientFirstName(), request.patientLastName());

        CatedraReservations.Hold hold = catedra.createHold(
            agenda.externalId(),
            request.date(),
            slot.startTime(),
            patient.externalPatientId()
        );

        // Se guarda antes de confirmar: así, si el pedido de teléfono llega por Kafka antes que la
        // respuesta REST, ya hay un proceso al que aplicárselo.
        ReservationProcess process = processes.create(
            ReservationProcess.held(ownerId, appointment, patient, hold.holdId(), hold.reservationProcessId(), hold.expiresAt(), now())
        );
        return confirmInitially(process, false);
    }

    /**
     * Reintenta la confirmación inicial de un proceso que quedó HELD. En cualquier otro estado no
     * hay nada que reintentar y devuelve el proceso como está.
     */
    public ReservationProcess retryConfirmation(long ownerId, long id) {
        ReservationProcess process = get(ownerId, id);
        if (process.status() != ReservationStatus.HELD) {
            return process;
        }
        return confirmInitially(process, true);
    }

    /**
     * Un usuario solo ve sus procesos. Uno ajeno da el mismo error que uno que no existe.
     */
    public ReservationProcess get(long ownerId, long id) {
        return processes
            .findById(id)
            .filter(p -> p.belongsTo(ownerId))
            .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    private ReservationProcess confirmInitially(ReservationProcess process, boolean failIfUnavailable) {
        Instant newExpiresAt;
        try {
            newExpiresAt = catedra.confirmHold(process.holdId(), process.patient());
        } catch (CatedraRejectedException e) {
            return processes.update(process.id(), p -> applyRejection(p, e));
        } catch (CatedraUnavailableException e) {
            // La cátedra no respondió: no sabemos si la confirmación llegó. El proceso queda HELD;
            // si llegó, el reintento recibe RESERVATION_PROCESS_ALREADY_CONFIRMED y avanza igual.
            if (failIfUnavailable) {
                throw e;
            }
            return process;
        }
        return processes.update(process.id(), p -> p.initialConfirmationAccepted(newExpiresAt, now()));
    }

    private void applyRejection(ReservationProcess process, CatedraRejectedException e) {
        switch (e.code()) {
            // Un intento anterior sí llegó, aunque no vimos la respuesta
            case "RESERVATION_PROCESS_ALREADY_CONFIRMED" -> process.initialConfirmationAccepted(null, now());
            case "HOLD_EXPIRED" -> process.expired(e.code(), now());
            default -> process.failed(e.code(), now());
        }
    }

    private Instant now() {
        return clock.instant();
    }

    /**
     * Lo que pide el usuario. professionalId es el id local del catálogo.
     */
    public record StartReservation(
        long professionalId,
        LocalDate date,
        LocalTime startTime,
        String patientFirstName,
        String patientLastName
    ) {}
}
