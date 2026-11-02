package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API REST de la cátedra y el JWT técnico de la cuenta de integración (nunca en el repo).
 */
@ConfigurationProperties(prefix = "catedra")
public record CatedraProperties(String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {
    public CatedraProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
