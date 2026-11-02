package ar.edu.um.turnos.appointments.reservation.infrastructure.web;

import static org.hamcrest.Matchers.contains;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.turnos.appointments.IntegrationTest;
import ar.edu.um.turnos.appointments.reservation.domain.CatalogUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalNotFoundException;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import ar.edu.um.turnos.appointments.reservation.ports.out.ProfessionalAgendaSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class AvailabilityIT {

    private static final String URL = "/api/availability";
    private static final LocalDate PROXIMO_LUNES = LocalDate.now(ZoneId.of("America/Argentina/Mendoza")).with(
        TemporalAdjusters.next(DayOfWeek.MONDAY)
    );

    @Autowired
    private MockMvc mockMvc;

    // El catálogo y la cátedra se reemplazan: acá se prueba el servicio de turnos
    @MockitoBean
    private ProfessionalAgendaSource agendas;

    @MockitoBean
    private OccupancySource occupancies;

    @Test
    @WithMockUser
    void devuelveLosTurnosLibres() throws Exception {
        when(agendas.agendaOf(1051)).thenReturn(agendaDeLunes());
        when(occupancies.occupancies(15, PROXIMO_LUNES, PROXIMO_LUNES)).thenReturn(
            List.of(new Occupancy(PROXIMO_LUNES, LocalTime.of(9, 30), Occupancy.Status.CONFIRMED, null))
        );

        mockMvc
            .perform(get(URL).param("professionalId", "1051").param("date", PROXIMO_LUNES.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.professionalName").value("Ana Torres"))
            .andExpect(jsonPath("$.bookable").value(true))
            .andExpect(jsonPath("$.slots[*].startTime").value(contains("09:00:00", "10:00:00", "10:30:00")));
    }

    @Test
    @WithMockUser
    void unaFechaPasadaDa400() throws Exception {
        mockMvc
            .perform(get(URL).param("professionalId", "1051").param("date", "2020-01-06"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser
    void unProfesionalQueNoExisteDa404() throws Exception {
        when(agendas.agendaOf(anyLong())).thenThrow(new ProfessionalNotFoundException(999));

        mockMvc
            .perform(get(URL).param("professionalId", "999").param("date", PROXIMO_LUNES.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void siElCatalogoNoRespondeDa503() throws Exception {
        when(agendas.agendaOf(anyLong())).thenThrow(new CatalogUnavailableException("timeout", null));

        mockMvc
            .perform(get(URL).param("professionalId", "1051").param("date", PROXIMO_LUNES.toString()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CATALOG_UNAVAILABLE"));
    }

    @Test
    void sinUsuarioDa401() throws Exception {
        mockMvc
            .perform(get(URL).param("professionalId", "1051").param("date", PROXIMO_LUNES.toString()))
            .andExpect(status().isUnauthorized());
    }

    private static ProfessionalAgenda agendaDeLunes() {
        return new ProfessionalAgenda(
            1051,
            15,
            "Ana",
            "Torres",
            "Cardiologia",
            true,
            1L,
            List.of(new ProfessionalAgenda.Schedule(21, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
        );
    }
}
