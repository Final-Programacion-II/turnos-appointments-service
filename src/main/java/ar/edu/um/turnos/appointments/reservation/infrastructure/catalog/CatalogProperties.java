package ar.edu.um.turnos.appointments.reservation.infrastructure.catalog;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Dónde está el servicio de catálogo y cuánto se lo espera.
 */
@ConfigurationProperties(prefix = "catalog")
public record CatalogProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    public CatalogProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
