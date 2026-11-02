package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;

/**
 * De dónde sale la agenda vigente de un profesional.
 */
public interface ProfessionalAgendaSource {
    /**
     * @throws ar.edu.um.turnos.appointments.reservation.domain.ProfessionalNotFoundException si el catálogo no lo tiene
     * @throws ar.edu.um.turnos.appointments.reservation.domain.CatalogUnavailableException si el catálogo no responde
     */
    ProfessionalAgenda agendaOf(long professionalId);
}
