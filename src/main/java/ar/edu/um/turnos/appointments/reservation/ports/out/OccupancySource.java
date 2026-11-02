package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import java.time.LocalDate;
import java.util.List;

/**
 * Turnos ocupados según la cátedra (confirmados y holds activos).
 */
public interface OccupancySource {
    /**
     * @param professionalExternalId id del profesional en la cátedra (no el id local del catálogo)
     */
    List<Occupancy> occupancies(long professionalExternalId, LocalDate from, LocalDate to);
}
