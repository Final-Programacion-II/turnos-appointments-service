package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraAppointment;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CatedraAppointmentsAdapterTest {

    private HttpServer server;
    private final List<String> queries = new ArrayList<>();
    private String authorizationRecibido;

    @AfterEach
    void apagar() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void traeLasReservasDelPaciente() throws Exception {
        catedraQueResponde(
            200,
            List.of(
                """
                [ { "reservationId": 8251, "reservationProcessId": "process-1", "externalPatientId": "user-42",
                    "appointmentDate": "2026-09-14", "startTime": "09:30:00", "status": "CONFIRMED" },
                  { "reservationId": 8252, "reservationProcessId": "process-2", "status": "CANCELLED",
                    "cancellationReason": "Cambio solicitado por el usuario" },
                  { "reservationId": 8253, "reservationProcessId": "process-3", "status": "ALGO_NUEVO" } ]
                """
            )
        );

        List<CatedraAppointment> reservas = adapter().appointmentsOf("user-42");

        assertThat(authorizationRecibido).isEqualTo("Bearer token-tecnico");
        assertThat(queries).containsExactly("externalPatientId=user-42&page=0&size=50");
        // El estado desconocido se descarta
        assertThat(reservas).hasSize(2);
        assertThat(reservas.get(0).status()).isEqualTo(CatedraAppointment.Status.CONFIRMED);
        assertThat(reservas.get(0).reservationId()).isEqualTo(8251L);
        assertThat(reservas.get(1).cancellationReason()).isEqualTo("Cambio solicitado por el usuario");
    }

    @Test
    void pidePaginasHastaQueVengaUnaIncompleta() throws Exception {
        catedraQueResponde(200, List.of(pagina(0, 50), pagina(50, 3)));

        List<CatedraAppointment> reservas = adapter().appointmentsOf("user-42");

        assertThat(reservas).hasSize(53);
        assertThat(queries).containsExactly("externalPatientId=user-42&page=0&size=50", "externalPatientId=user-42&page=1&size=50");
    }

    @Test
    void siLaCatedraFallaNoSePuedeReconciliar() throws Exception {
        catedraQueResponde(500, List.of("{\"detail\":\"Failure during data access\"}"));

        assertThatThrownBy(() -> adapter().appointmentsOf("user-42")).isInstanceOf(CatedraUnavailableException.class);
    }

    private static String pagina(int desde, int cantidad) {
        return IntStream.range(desde, desde + cantidad)
            .mapToObj(i -> "{\"reservationProcessId\":\"process-" + i + "\",\"status\":\"FAILED\"}")
            .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Responde cada pedido con la siguiente respuesta de la lista.
     */
    private void catedraQueResponde(int status, List<String> respuestas) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/appointments", exchange -> {
            authorizationRecibido = exchange.getRequestHeaders().getFirst("Authorization");
            queries.add(exchange.getRequestURI().getQuery());
            String json = respuestas.get(Math.min(queries.size() - 1, respuestas.size() - 1));
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    private CatedraAppointmentsAdapter adapter() {
        String url = "http://localhost:" + server.getAddress().getPort();
        return new CatedraAppointmentsAdapter(new CatedraProperties(url, "token-tecnico", Duration.ofSeconds(1), Duration.ofSeconds(1)));
    }
}
