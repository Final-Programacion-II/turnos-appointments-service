package ar.edu.um.turnos.appointments.reservation.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.reservation.domain.CatalogUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalNotFoundException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CatalogRestAdapterTest {

    // Respuesta real del catálogo, con un campo de más (que se tiene que ignorar)
    private static final String AGENDA = """
        {
          "id": 1051, "externalId": 15, "firstName": "Ana", "lastName": "Torres",
          "categoryName": "Cardiologia", "bookable": true, "catalogVersion": 1, "campoNuevo": "x",
          "schedules": [
            { "externalId": 21, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00", "slotDurationMinutes": 30 }
          ]
        }
        """;

    private HttpServer server;
    private String authorizationRecibido;

    @BeforeEach
    void usuarioLogueado() {
        // Las credenciales de la autenticación son el JWT con el que entró el usuario
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("juan", "token-de-juan"));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void traeLaAgendaReenviandoElTokenDelUsuario() throws Exception {
        catalogoQueResponde(200, AGENDA);

        ProfessionalAgenda agenda = adapter().agendaOf(1051);

        assertThat(authorizationRecibido).isEqualTo("Bearer token-de-juan");
        assertThat(agenda.externalId()).isEqualTo(15);
        assertThat(agenda.bookable()).isTrue();
        assertThat(agenda.schedules()).hasSize(1);
        assertThat(agenda.schedules().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(agenda.schedules().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void unProfesionalQueNoExisteDa404() throws Exception {
        catalogoQueResponde(404, "{}");

        assertThatThrownBy(() -> adapter().agendaOf(999)).isInstanceOf(ProfessionalNotFoundException.class);
    }

    @Test
    void siElCatalogoFallaNoSePuedeSeguir() throws Exception {
        catalogoQueResponde(503, "{}");

        assertThatThrownBy(() -> adapter().agendaOf(1051)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void siElCatalogoEstaApagadoNoSePuedeSeguir() {
        // Nadie escucha en el puerto 1
        CatalogRestAdapter adapter = new CatalogRestAdapter(new CatalogProperties("http://localhost:1", null, null));

        assertThatThrownBy(() -> adapter.agendaOf(1051)).isInstanceOf(CatalogUnavailableException.class);
    }

    @Test
    void sinUsuarioNoLlamaAlCatalogo() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() ->
            new CatalogRestAdapter(new CatalogProperties("http://localhost:1", null, null)).agendaOf(1051)
        ).isInstanceOf(IllegalStateException.class);
    }

    private void catalogoQueResponde(int status, String json) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/professionals", exchange -> {
            authorizationRecibido = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private CatalogRestAdapter adapter() {
        String url = "http://localhost:" + server.getAddress().getPort();
        return new CatalogRestAdapter(new CatalogProperties(url, Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
