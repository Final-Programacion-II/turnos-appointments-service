package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba el adaptador contra un servidor HTTP local que responde como la cátedra
 * (ejemplos de las secciones 9, 10 y 13 de la referencia).
 */
class CatedraHoldAdapterTest {

    private static final String HOLD_CREADO = """
        { "holdId": "hold-6c0c2f41", "reservationProcessId": "process-a92b47d0",
          "status": "HELD", "expiresAt": "2026-09-10T14:44:50Z" }
        """;

    private static final String CONFIRMACION_ACEPTADA = """
        { "reservationProcessId": "process-a92b47d0", "status": "WAITING_FOR_PHONE",
          "expiresAt": "2026-09-10T14:49:50Z" }
        """;

    private static final String TURNO_RETENIDO = """
        { "type": "https://www.jhipster.tech/problem/problem-with-message", "title": "Conflict",
          "status": 409, "detail": "Slot already held", "message": "error.http.409",
          "path": "/api/appointment-holds", "code": "SLOT_ALREADY_HELD" }
        """;

    private HttpServer server;
    private String metodoRecibido;
    private String pathRecibido;
    private String authorizationRecibido;
    private String bodyRecibido;

    @AfterEach
    void apagar() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void creaElHoldConElFormatoDeLaReferencia() throws Exception {
        catedraQueResponde(201, "application/json", HOLD_CREADO);

        CatedraReservations.Hold hold = adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), "user-42");

        assertThat(metodoRecibido).isEqualTo("POST");
        assertThat(pathRecibido).isEqualTo("/api/appointment-holds");
        assertThat(authorizationRecibido).isEqualTo("Bearer token-tecnico");
        assertThat(bodyRecibido)
            .contains("\"professionalId\":15")
            .contains("\"date\":\"2026-09-14\"")
            .contains("\"startTime\":\"09:30\"")
            .contains("\"externalPatientId\":\"user-42\"");
        assertThat(hold.holdId()).isEqualTo("hold-6c0c2f41");
        assertThat(hold.reservationProcessId()).isEqualTo("process-a92b47d0");
        assertThat(hold.expiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
    }

    @Test
    void confirmaElHold() throws Exception {
        catedraQueResponde(202, "application/json", CONFIRMACION_ACEPTADA);

        Instant vence = adapter().confirmHold("hold-6c0c2f41", new Patient("user-42", "Juan", "Perez"));

        assertThat(pathRecibido).isEqualTo("/api/appointment-holds/hold-6c0c2f41/confirm");
        assertThat(bodyRecibido)
            .contains("\"externalPatientId\":\"user-42\"")
            .contains("\"patientFirstName\":\"Juan\"")
            .contains("\"patientLastName\":\"Perez\"");
        assertThat(vence).isEqualTo(Instant.parse("2026-09-10T14:49:50Z"));
    }

    @Test
    void unRechazoTraeElCodeDeLaCatedra() throws Exception {
        catedraQueResponde(409, "application/problem+json", TURNO_RETENIDO);

        assertThatThrownBy(() ->
            adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), "user-42")
        ).isInstanceOfSatisfying(CatedraRejectedException.class, e -> {
            assertThat(e.httpStatus()).isEqualTo(409);
            assertThat(e.code()).isEqualTo("SLOT_ALREADY_HELD");
        });
    }

    @Test
    void unRechazoSinCodeIgualSeInforma() throws Exception {
        catedraQueResponde(400, "text/plain", "Bad Request");

        assertThatThrownBy(() -> adapter().confirmHold("hold-1", new Patient("user-42", "Juan", "Perez"))).isInstanceOfSatisfying(
            CatedraRejectedException.class,
            e -> assertThat(e.code()).isEqualTo("CATEDRA_REJECTED")
        );
    }

    @Test
    void siLaCuentaTecnicaNoSirveNoEsCulpaDelUsuario() throws Exception {
        catedraQueResponde(401, "application/json", "{}");

        assertThatThrownBy(() -> adapter().createHold(15, LocalDate.of(2026, 9, 14), LocalTime.of(9, 30), "user-42")).isInstanceOf(
            CatedraUnavailableException.class
        );
    }

    @Test
    void unErrorDelServidorEsIndisponibilidad() throws Exception {
        catedraQueResponde(500, "application/json", "{}");

        assertThatThrownBy(() -> adapter().confirmHold("hold-1", new Patient("user-42", "Juan", "Perez"))).isInstanceOf(
            CatedraUnavailableException.class
        );
    }

    @Test
    void sinConexionEsIndisponibilidad() {
        // Nadie escucha en el puerto 1
        CatedraHoldAdapter adapter = new CatedraHoldAdapter(
            new CatedraProperties("http://localhost:1", "token-tecnico", Duration.ofSeconds(1), Duration.ofSeconds(1))
        );

        assertThatThrownBy(() -> adapter.confirmHold("hold-1", new Patient("user-42", "Juan", "Perez"))).isInstanceOf(
            CatedraUnavailableException.class
        );
    }

    private void catedraQueResponde(int status, String contentType, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/appointment-holds", exchange -> {
            metodoRecibido = exchange.getRequestMethod();
            pathRecibido = exchange.getRequestURI().getPath();
            authorizationRecibido = exchange.getRequestHeaders().getFirst("Authorization");
            bodyRecibido = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] respuesta = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, respuesta.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(respuesta);
            }
        });
        server.start();
    }

    private CatedraHoldAdapter adapter() {
        String url = "http://localhost:" + server.getAddress().getPort();
        return new CatedraHoldAdapter(new CatedraProperties(url, "token-tecnico", Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
