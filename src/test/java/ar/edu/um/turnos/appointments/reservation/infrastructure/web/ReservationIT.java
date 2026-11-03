package ar.edu.um.turnos.appointments.reservation.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.turnos.appointments.IntegrationTest;
import ar.edu.um.turnos.appointments.reservation.application.CatedraEvent;
import ar.edu.um.turnos.appointments.reservation.application.CatedraEventService;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import ar.edu.um.turnos.appointments.reservation.ports.out.PhoneSubmissionPublisher;
import ar.edu.um.turnos.appointments.reservation.ports.out.ProfessionalAgendaSource;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.EventResult;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reservas de punta a punta dentro del servicio: HTTP, servicio y PostgreSQL de verdad. Solo se
 * reemplazan el catálogo y la cátedra.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ReservationIT {

    private static final String URL = "/api/reservations";
    private static final LocalDate PROXIMO_LUNES = LocalDate.now(ZoneId.of("America/Argentina/Mendoza")).with(
        TemporalAdjusters.next(DayOfWeek.MONDAY)
    );
    private static final Instant VENCE = Instant.now().plus(15, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);

    // Usuarios de ejemplo que JHipster carga en la base: 1 = admin, 2 = user
    private static final long ANA = 1;
    private static final long BRUNO = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private ProfessionalAgendaSource agendas;

    @MockitoBean
    private OccupancySource occupancies;

    @MockitoBean
    private CatedraReservations catedra;

    @MockitoBean
    private PhoneSubmissionPublisher phonePublisher;

    // El listener de Kafka está apagado en los tests: los eventos se aplican llamando a este servicio
    @Autowired
    private CatedraEventService catedraEvents;

    @BeforeEach
    void catalogoConUnaAgenda() {
        when(agendas.agendaOf(1051)).thenReturn(
            new ProfessionalAgenda(
                1051,
                15,
                "Ana",
                "Torres",
                "Cardiologia",
                true,
                1L,
                List.of(new ProfessionalAgenda.Schedule(21, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
            )
        );
        when(occupancies.occupancies(anyLong(), any(), any())).thenReturn(List.of());
    }

    @Test
    void reservaYConsultaSuReserva() throws Exception {
        holdYConfirmacionOk();

        String respuesta = mockMvc
            .perform(post(URL).with(usuario(ANA)).contentType(MediaType.APPLICATION_JSON).content(pedido("09:30")))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"))
            .andExpect(jsonPath("$.professionalName").value("Ana Torres"))
            .andExpect(jsonPath("$.startTime").value("09:30:00"))
            .andExpect(jsonPath("$.endTime").value("10:00:00"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Number id = JsonPath.read(respuesta, "$.id");

        // Se escribe en la base y se olvida la copia en memoria: el GET lee de PostgreSQL
        em.flush();
        em.clear();

        mockMvc
            .perform(get(URL + "/" + id).with(usuario(ANA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"))
            .andExpect(jsonPath("$.date").value(PROXIMO_LUNES.toString()))
            // La hora se guarda como hora de reloj, sin correrse por la zona horaria
            .andExpect(jsonPath("$.startTime").value("09:30:00"));
    }

    @Test
    void otroUsuarioNoVeNiReintentaUnaReservaAjena() throws Exception {
        holdYConfirmacionOk();
        Number id = reservar(ANA, "09:30");

        mockMvc
            .perform(get(URL + "/" + id).with(usuario(BRUNO)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESERVATION_NOT_FOUND"));
        mockMvc.perform(post(URL + "/" + id + "/confirm").with(usuario(BRUNO))).andExpect(status().isNotFound());
    }

    @Test
    void unTurnoOcupadoDa409() throws Exception {
        when(occupancies.occupancies(anyLong(), any(), any())).thenReturn(
            List.of(new Occupancy(PROXIMO_LUNES, LocalTime.of(9, 30), Occupancy.Status.CONFIRMED, null))
        );

        mockMvc
            .perform(post(URL).with(usuario(ANA)).contentType(MediaType.APPLICATION_JSON).content(pedido("09:30")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SLOT_NOT_AVAILABLE"));
        verify(catedra, never()).createHold(anyLong(), any(), any(), anyString());
    }

    @Test
    void siLaCatedraRechazaElHoldSeInformaSuCode() throws Exception {
        when(catedra.createHold(anyLong(), any(), any(), anyString())).thenThrow(
            new CatedraRejectedException(409, "SLOT_ALREADY_HELD", "Slot already held")
        );

        mockMvc
            .perform(post(URL).with(usuario(ANA)).contentType(MediaType.APPLICATION_JSON).content(pedido("09:30")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SLOT_ALREADY_HELD"));
    }

    @Test
    void siLaConfirmacionFallaSeReintenta() throws Exception {
        when(catedra.createHold(15, PROXIMO_LUNES, LocalTime.of(9, 30), "user-1")).thenReturn(
            new CatedraReservations.Hold("hold-1", "process-1", VENCE)
        );
        when(catedra.confirmHold(eq("hold-1"), any()))
            .thenThrow(new CatedraUnavailableException("timeout", null))
            .thenReturn(VENCE);

        String respuesta = mockMvc
            .perform(post(URL).with(usuario(ANA)).contentType(MediaType.APPLICATION_JSON).content(pedido("09:30")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("HELD"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        Number id = JsonPath.read(respuesta, "$.id");

        mockMvc
            .perform(post(URL + "/" + id + "/confirm").with(usuario(ANA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"));
    }

    @Test
    void faltanDatosDelPaciente() throws Exception {
        String sinApellido = """
            { "professionalId": 1051, "date": "%s", "startTime": "09:30", "patientFirstName": "Juan" }
            """.formatted(PROXIMO_LUNES);

        mockMvc
            .perform(post(URL).with(usuario(ANA)).contentType(MediaType.APPLICATION_JSON).content(sinApellido))
            .andExpect(status().isBadRequest());
        verify(catedra, never()).createHold(anyLong(), any(), any(), anyString());
    }

    @Test
    void flujoCompletoConTelefonoYConfirmacion() throws Exception {
        holdYConfirmacionOk();
        Number id = reservar(ANA, "09:30");

        // La cátedra pide el teléfono
        assertThat(catedraEvents.handle(evento("request-1", CatedraEvent.Type.PHONE_REQUESTED))).isEqualTo(EventResult.APPLIED);
        enLaBase();
        mockMvc
            .perform(get(URL + "/" + id).with(usuario(ANA)))
            .andExpect(jsonPath("$.status").value("WAITING_FOR_PHONE"))
            .andExpect(jsonPath("$.phoneRequested").value(true));

        // El usuario lo manda
        mockMvc
            .perform(
                post(URL + "/" + id + "/phone")
                    .with(usuario(ANA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telefono("+54 9 261 555-1234"))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PHONE_SUBMITTED"));
        verify(phonePublisher, times(1)).publish(any());

        // La cátedra confirma; el mismo evento repetido y un vencimiento tardío no cambian nada
        assertThat(catedraEvents.handle(evento("confirmation-1", CatedraEvent.Type.CONFIRMED))).isEqualTo(EventResult.APPLIED);
        assertThat(catedraEvents.handle(evento("confirmation-1", CatedraEvent.Type.CONFIRMED))).isEqualTo(EventResult.DUPLICATE);
        catedraEvents.handle(evento("expired-1", CatedraEvent.Type.EXPIRED));
        enLaBase();

        mockMvc
            .perform(get(URL + "/" + id).with(usuario(ANA)))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.phoneRequested").value(false));
    }

    @Test
    void elTelefonoAntesDeQueLaCatedraLoPidaDa409() throws Exception {
        holdYConfirmacionOk();
        Number id = reservar(ANA, "09:30");

        mockMvc
            .perform(
                post(URL + "/" + id + "/phone")
                    .with(usuario(ANA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telefono("+5492615551234"))
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_RESERVATION_STATE"));
        verify(phonePublisher, never()).publish(any());
    }

    @Test
    void unTelefonoInvalidoDa400() throws Exception {
        holdYConfirmacionOk();
        Number id = reservar(ANA, "09:30");
        catedraEvents.handle(evento("request-1", CatedraEvent.Type.PHONE_REQUESTED));

        mockMvc
            .perform(
                post(URL + "/" + id + "/phone")
                    .with(usuario(ANA))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(telefono("123"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"));
        verify(phonePublisher, never()).publish(any());
    }

    @Test
    void sinUsuarioDa401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(pedido("09:30"))).andExpect(status().isUnauthorized());
    }

    private void holdYConfirmacionOk() {
        when(catedra.createHold(eq(15L), eq(PROXIMO_LUNES), any(), anyString())).thenReturn(
            new CatedraReservations.Hold("hold-1", "process-1", VENCE)
        );
        when(catedra.confirmHold(eq("hold-1"), any())).thenReturn(VENCE);
    }

    private Number reservar(long usuario, String hora) throws Exception {
        String respuesta = mockMvc
            .perform(post(URL).with(usuario(usuario)).contentType(MediaType.APPLICATION_JSON).content(pedido(hora)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return JsonPath.read(respuesta, "$.id");
    }

    private static CatedraEvent evento(String eventId, CatedraEvent.Type tipo) {
        Long reservationId = tipo == CatedraEvent.Type.CONFIRMED ? 8251L : null;
        return new CatedraEvent(eventId, tipo, "process-1", reservationId, VENCE, "HOLD_EXPIRED", null);
    }

    /**
     * Escribe en la base y olvida la copia en memoria: lo siguiente se lee de PostgreSQL.
     */
    private void enLaBase() {
        em.flush();
        em.clear();
    }

    private static String telefono(String numero) {
        return "{ \"phoneNumber\": \"" + numero + "\" }";
    }

    private static String pedido(String hora) {
        return """
        { "professionalId": 1051, "date": "%s", "startTime": "%s",
          "patientFirstName": "Juan", "patientLastName": "Perez" }
        """.formatted(PROXIMO_LUNES, hora);
    }

    /**
     * Un JWT como los que emite este servicio, con el claim userId.
     */
    private static JwtRequestPostProcessor usuario(long userId) {
        return jwt().jwt(token -> token.subject("usuario-" + userId).claim("userId", userId));
    }
}
