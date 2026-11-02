package ar.edu.um.turnos.appointments.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityTest {

    private static final ZoneId MENDOZA = ZoneId.of("America/Argentina/Mendoza");
    // Lunes 2/11/2026 al mediodía
    private static final Clock CLOCK = Clock.fixed(ZonedDateTime.of(2026, 11, 2, 12, 0, 0, 0, MENDOZA).toInstant(), MENDOZA);
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);

    @Test
    void armaLosTurnosDelHorario() {
        List<AvailableSlot> slots = Availability.of(agenda("09:00", "11:00"), LUNES, List.of(), CLOCK);

        assertThat(inicios(slots)).containsExactly("09:00", "09:30", "10:00", "10:30");
        assertThat(slots.get(0).endTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void unTurnoQueNoEntraCompletoNoSeOfrece() {
        // De 10:00 a 10:30 se pasaría del fin del horario
        List<AvailableSlot> slots = Availability.of(agenda("09:00", "10:15"), LUNES, List.of(), CLOCK);

        assertThat(inicios(slots)).containsExactly("09:00", "09:30");
    }

    @Test
    void sacaLosConfirmadosYLosHoldsActivos() {
        List<Occupancy> ocupados = List.of(
            new Occupancy(LUNES, LocalTime.of(9, 0), Occupancy.Status.CONFIRMED, null),
            new Occupancy(LUNES, LocalTime.of(9, 30), Occupancy.Status.HELD, CLOCK.instant().plusSeconds(300))
        );

        assertThat(inicios(Availability.of(agenda("09:00", "11:00"), LUNES, ocupados, CLOCK))).containsExactly("10:00", "10:30");
    }

    @Test
    void unHoldVencidoYaNoOcupa() {
        List<Occupancy> ocupados = List.of(
            new Occupancy(LUNES, LocalTime.of(10, 0), Occupancy.Status.HELD, CLOCK.instant().minusSeconds(1))
        );

        assertThat(inicios(Availability.of(agenda("09:00", "11:00"), LUNES, ocupados, CLOCK))).contains("10:00");
    }

    @Test
    void unaOcupacionDeOtraFechaNoAfecta() {
        List<Occupancy> ocupados = List.of(new Occupancy(LUNES.plusWeeks(1), LocalTime.of(9, 0), Occupancy.Status.CONFIRMED, null));

        assertThat(inicios(Availability.of(agenda("09:00", "11:00"), LUNES, ocupados, CLOCK))).contains("09:00");
    }

    @Test
    void otroDiaDeLaSemanaNoTieneTurnos() {
        assertThat(Availability.of(agenda("09:00", "11:00"), LUNES.plusDays(1), List.of(), CLOCK)).isEmpty();
    }

    @Test
    void hoyNoOfreceLosTurnosQueYaPasaron() {
        Clock nueveYCuarenta = Clock.fixed(ZonedDateTime.of(2026, 11, 2, 9, 40, 0, 0, MENDOZA).toInstant(), MENDOZA);
        LocalDate hoy = LocalDate.of(2026, 11, 2);

        assertThat(inicios(Availability.of(agenda("09:00", "11:00"), hoy, List.of(), nueveYCuarenta))).containsExactly("10:00", "10:30");
    }

    @Test
    void unProfesionalQueNoSePuedeReservarNoTieneTurnos() {
        ProfessionalAgenda deBaja = new ProfessionalAgenda(
            1,
            15,
            "Ana",
            "Torres",
            "Cardiologia",
            false,
            1L,
            agenda("09:00", "11:00").schedules()
        );

        assertThat(Availability.of(deBaja, LUNES, List.of(), CLOCK)).isEmpty();
    }

    private static ProfessionalAgenda agenda(String desde, String hasta) {
        return new ProfessionalAgenda(
            1,
            15,
            "Ana",
            "Torres",
            "Cardiologia",
            true,
            1L,
            List.of(new ProfessionalAgenda.Schedule(21, DayOfWeek.MONDAY, LocalTime.parse(desde), LocalTime.parse(hasta), 30))
        );
    }

    private static List<String> inicios(List<AvailableSlot> slots) {
        return slots
            .stream()
            .map(s -> s.startTime().toString())
            .toList();
    }
}
