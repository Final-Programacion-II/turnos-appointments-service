package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

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

    /**
     * Cliente HTTP con los timeouts configurados. Sin timeout, una cátedra colgada dejaría
     * colgado también el pedido del usuario.
     */
    RestClient restClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder()
            .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : "http://localhost")
            .requestFactory(requestFactory)
            .build();
    }

    void requireConfigured() {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(token)) {
            throw new IllegalStateException("Falta configurar CATEDRA_BASE_URL o CATEDRA_TOKEN");
        }
    }

    String authorization() {
        return "Bearer " + token;
    }
}
