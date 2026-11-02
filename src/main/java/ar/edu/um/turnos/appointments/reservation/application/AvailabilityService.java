package ar.edu.um.turnos.appointments.reservation.application;

import ar.edu.um.turnos.appointments.reservation.domain.Availability;
import ar.edu.um.turnos.appointments.reservation.domain.AvailableSlot;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import ar.edu.um.turnos.appointments.reservation.ports.out.ProfessionalAgendaSource;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Turnos libres de un profesional en una fecha. Cada consulta pide la agenda vigente al
 * catálogo y las ocupaciones a la cátedra: no se guarda nada, porque las dos cambian.
 */
public class AvailabilityService {

    private final ProfessionalAgendaSource agendas;
    private final OccupancySource occupancies;
    private final Clock clock;

    public AvailabilityService(ProfessionalAgendaSource agendas, OccupancySource occupancies, Clock clock) {
        this.agendas = agendas;
        this.occupancies = occupancies;
        this.clock = clock;
    }

    public Result availability(long professionalId, LocalDate date) {
        if (date.isBefore(LocalDate.now(clock))) {
            throw new IllegalArgumentException("La fecha " + date + " ya pasó");
        }
        ProfessionalAgenda agenda = agendas.agendaOf(professionalId);
        if (!agenda.bookable()) {
            return new Result(agenda, date, List.of());
        }
        List<AvailableSlot> slots = Availability.of(agenda, date, occupancies.occupancies(agenda.externalId(), date, date), clock);
        return new Result(agenda, date, slots);
    }

    public record Result(ProfessionalAgenda agenda, LocalDate date, List<AvailableSlot> slots) {}
}
