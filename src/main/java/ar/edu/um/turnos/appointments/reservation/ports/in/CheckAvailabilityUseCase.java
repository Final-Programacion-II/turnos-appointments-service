package ar.edu.um.turnos.appointments.reservation.ports.in;

import ar.edu.um.turnos.appointments.reservation.domain.AvailableSlot;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import java.time.LocalDate;
import java.util.List;

/**
 * Consultar los turnos libres de un profesional en una fecha.
 */
public interface CheckAvailabilityUseCase {
    /**
     * @param professionalId id local del catálogo.
     */
    Result availability(long professionalId, LocalDate date);

    record Result(ProfessionalAgenda agenda, LocalDate date, List<AvailableSlot> slots) {}
}
