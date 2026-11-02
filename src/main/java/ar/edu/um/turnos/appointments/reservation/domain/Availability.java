package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calcula los turnos libres de un profesional en una fecha: los slots que salen de sus
 * horarios semanales, menos los que la cátedra informa como ocupados y menos los que ya pasaron.
 */
public final class Availability {

    private Availability() {}

    public static List<AvailableSlot> of(ProfessionalAgenda agenda, LocalDate date, List<Occupancy> occupancies, Clock clock) {
        if (!agenda.bookable()) {
            return List.of();
        }
        Instant now = clock.instant();
        LocalDateTime nowLocal = LocalDateTime.now(clock);

        Set<LocalTime> occupied = occupancies
            .stream()
            .filter(o -> o.date().equals(date) && o.blocksAt(now))
            .map(Occupancy::startTime)
            .collect(Collectors.toSet());

        List<AvailableSlot> slots = new ArrayList<>();
        for (ProfessionalAgenda.Schedule schedule : agenda.schedules()) {
            if (schedule.dayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            LocalTime start = schedule.startTime();
            LocalTime end = start.plusMinutes(schedule.slotDurationMinutes());
            // !end.isBefore(start) corta si el slot cruzara la medianoche
            while (!end.isAfter(schedule.endTime()) && !end.isBefore(start)) {
                boolean alreadyPassed = !LocalDateTime.of(date, start).isAfter(nowLocal);
                if (!occupied.contains(start) && !alreadyPassed) {
                    slots.add(new AvailableSlot(start, end));
                }
                start = end;
                end = start.plusMinutes(schedule.slotDurationMinutes());
            }
        }
        slots.sort(Comparator.comparing(AvailableSlot::startTime));
        return slots;
    }
}
