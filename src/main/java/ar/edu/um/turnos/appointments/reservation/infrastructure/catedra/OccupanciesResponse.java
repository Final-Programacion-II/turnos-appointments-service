package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * JSON de GET /api/appointment-occupancies (sección 8 de la referencia de integración).
 */
record OccupanciesResponse(long professionalId, LocalDate from, LocalDate to, List<Item> occupancies) {
    record Item(LocalDate date, LocalTime startTime, Occupancy.Status status, Instant expiresAt) {}

    List<Occupancy> toDomain() {
        return occupancies
            .stream()
            .map(o -> new Occupancy(o.date(), o.startTime(), o.status(), o.expiresAt()))
            .toList();
    }
}
