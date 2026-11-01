package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Agenda vigente de un profesional, como la informa el servicio de catálogo.
 * professionalId es el id local del catálogo; externalId es el que conoce la cátedra.
 */
public record ProfessionalAgenda(
    long professionalId,
    long externalId,
    String firstName,
    String lastName,
    String categoryName,
    boolean bookable,
    Long catalogVersion,
    List<Schedule> schedules
) {
    public ProfessionalAgenda {
        schedules = List.copyOf(schedules);
    }

    public record Schedule(long externalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int slotDurationMinutes) {}
}
