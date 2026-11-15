package ar.edu.um.turnos.appointments.reservation.infrastructure.web;

import ar.edu.um.turnos.appointments.reservation.domain.AvailableSlot;
import ar.edu.um.turnos.appointments.reservation.ports.in.CheckAvailabilityUseCase;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Turnos libres de un profesional en una fecha, para que el usuario elija uno.
 */
@RestController
@RequestMapping("/api/availability")
public class AvailabilityResource {

    private final CheckAvailabilityUseCase availability;

    public AvailabilityResource(CheckAvailabilityUseCase availability) {
        this.availability = availability;
    }

    /**
     * {@code GET /api/availability?professionalId=1051&date=2026-11-09}
     *
     * @param professionalId id local del profesional, el que devuelve la búsqueda del catálogo.
     */
    @GetMapping
    public AvailabilityDTO getAvailability(@RequestParam("professionalId") long professionalId, @RequestParam("date") LocalDate date) {
        CheckAvailabilityUseCase.Result result = availability.availability(professionalId, date);
        return new AvailabilityDTO(
            result.agenda().professionalId(),
            result.agenda().firstName() + " " + result.agenda().lastName(),
            result.agenda().categoryName(),
            result.date(),
            result.agenda().bookable(),
            result.slots().stream().map(SlotDTO::of).toList()
        );
    }

    public record AvailabilityDTO(
        long professionalId,
        String professionalName,
        String categoryName,
        LocalDate date,
        boolean bookable,
        List<SlotDTO> slots
    ) {}

    public record SlotDTO(LocalTime startTime, LocalTime endTime) {
        static SlotDTO of(AvailableSlot slot) {
            return new SlotDTO(slot.startTime(), slot.endTime());
        }
    }
}
