package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Llamadas a la API de la cátedra con el JWT técnico de la cuenta de integración.
 */
@Component
@EnableConfigurationProperties(CatedraProperties.class)
public class CatedraRestAdapter implements OccupancySource {

    private final CatedraProperties properties;
    private final RestClient restClient;

    public CatedraRestAdapter(CatedraProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
            .baseUrl(StringUtils.hasText(properties.baseUrl()) ? properties.baseUrl() : "http://localhost")
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public List<Occupancy> occupancies(long professionalExternalId, LocalDate from, LocalDate to) {
        checkConfigured();
        try {
            OccupanciesResponse response = restClient
                .get()
                .uri("/api/appointment-occupancies?professionalId={id}&from={from}&to={to}", professionalExternalId, from, to)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
                .retrieve()
                .body(OccupanciesResponse.class);
            return response == null ? List.of() : response.toDomain();
        } catch (RestClientException e) {
            throw new CatedraUnavailableException("No se pudieron consultar las ocupaciones en la cátedra: " + e.getMessage(), e);
        }
    }

    private void checkConfigured() {
        if (!StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.token())) {
            throw new IllegalStateException("Falta configurar CATEDRA_BASE_URL o CATEDRA_TOKEN");
        }
    }
}
