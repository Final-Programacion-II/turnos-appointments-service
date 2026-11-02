package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CatedraRestAdapterTest {

    // Ejemplo de la sección 8 de la referencia de integración
    private static final String OCUPACIONES = """
        {
          "professionalId": 15, "from": "2026-09-14", "to": "2026-09-14",
          "occupancies": [
            { "date": "2026-09-14", "startTime": "09:00:00", "status": "CONFIRMED", "expiresAt": null },
            { "date": "2026-09-14", "startTime": "09:30:00", "status": "HELD", "expiresAt": "2026-09-10T14:44:50Z" }
          ]
        }
        """;

    private HttpServer server;
    private String authorizationRecibido;
    private String queryRecibida;

    @AfterEach
    void apagar() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void traeLasOcupacionesConElTokenTecnico() throws Exception {
        catedraQueResponde(200, OCUPACIONES);

        List<Occupancy> ocupaciones = adapter().occupancies(15, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14));

        assertThat(authorizationRecibido).isEqualTo("Bearer token-tecnico");
        assertThat(queryRecibida).isEqualTo("professionalId=15&from=2026-09-14&to=2026-09-14");
        assertThat(ocupaciones).hasSize(2);
        assertThat(ocupaciones.get(0).status()).isEqualTo(Occupancy.Status.CONFIRMED);
        assertThat(ocupaciones.get(1).startTime()).isEqualTo(LocalTime.of(9, 30));
        assertThat(ocupaciones.get(1).expiresAt()).isNotNull();
    }

    @Test
    void siLaCatedraFallaNoSePuedeSeguir() throws Exception {
        catedraQueResponde(500, "{}");

        assertThatThrownBy(() -> adapter().occupancies(15, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14))).isInstanceOf(
            CatedraUnavailableException.class
        );
    }

    @Test
    void sinTokenNoLlamaALaCatedra() {
        CatedraRestAdapter adapter = new CatedraRestAdapter(new CatedraProperties("http://localhost:1", "", null, null));

        assertThatThrownBy(() -> adapter.occupancies(15, LocalDate.of(2026, 9, 14), LocalDate.of(2026, 9, 14)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CATEDRA_TOKEN");
    }

    private void catedraQueResponde(int status, String json) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/appointment-occupancies", exchange -> {
            authorizationRecibido = exchange.getRequestHeaders().getFirst("Authorization");
            queryRecibida = exchange.getRequestURI().getQuery();
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private CatedraRestAdapter adapter() {
        String url = "http://localhost:" + server.getAddress().getPort();
        return new CatedraRestAdapter(new CatedraProperties(url, "token-tecnico", Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
