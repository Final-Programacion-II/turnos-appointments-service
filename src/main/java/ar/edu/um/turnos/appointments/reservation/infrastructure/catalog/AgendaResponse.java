package ar.edu.um.turnos.appointments.reservation.infrastructure.catalog;

import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * JSON de GET /api/professionals/{id}/agenda del catálogo. Se mantiene separado del dominio:
 * si el contrato cambia, se toca solo esta clase.
 */
record AgendaResponse(
    long id,
    long externalId,
    String firstName,
    String lastName,
    String categoryName,
    boolean bookable,
    Long catalogVersion,
    List<ScheduleResponse> schedules
) {
    record ScheduleResponse(long externalId, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime, int slotDurationMinutes) {}

    ProfessionalAgenda toDomain() {
        return new ProfessionalAgenda(
            id,
            externalId,
            firstName,
            lastName,
            categoryName,
            bookable,
            catalogVersion,
            schedules
                .stream()
                .map(s ->
                    new ProfessionalAgenda.Schedule(s.externalId(), s.dayOfWeek(), s.startTime(), s.endTime(), s.slotDurationMinutes())
                )
                .toList()
        );
    }
}
