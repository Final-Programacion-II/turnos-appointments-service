package ar.edu.um.turnos.appointments.reservation.infrastructure.catalog;

import ar.edu.um.turnos.appointments.reservation.domain.CatalogUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalNotFoundException;
import ar.edu.um.turnos.appointments.reservation.ports.out.ProfessionalAgendaSource;
import ar.edu.um.turnos.appointments.security.SecurityUtils;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Pide la agenda al servicio de catálogo reenviando el JWT del usuario que está operando
 * (decisión 4 de docs/decisiones.md). Así el catálogo valida firma y vencimiento, y cada
 * llamada queda asociada a un usuario.
 */
@Component
@EnableConfigurationProperties(CatalogProperties.class)
public class CatalogRestAdapter implements ProfessionalAgendaSource {

    private final RestClient restClient;

    public CatalogRestAdapter(CatalogProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
    }

    @Override
    public ProfessionalAgenda agendaOf(long professionalId) {
        String token = SecurityUtils.getCurrentUserJWT().orElseThrow(() ->
            new IllegalStateException("No hay un usuario autenticado para consultar el catálogo")
        );
        try {
            AgendaResponse response = restClient
                .get()
                .uri("/api/professionals/{id}/agenda", professionalId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .retrieve()
                .onStatus(
                    status -> status.isSameCodeAs(HttpStatus.NOT_FOUND),
                    (request, resp) -> {
                        throw new ProfessionalNotFoundException(professionalId);
                    }
                )
                .body(AgendaResponse.class);
            if (response == null) {
                throw new CatalogUnavailableException("El catálogo devolvió una agenda vacía", null);
            }
            return response.toDomain();
        } catch (RestClientException e) {
            // Incluye timeouts, conexión rechazada y respuestas 4xx/5xx que no sean 404
            throw new CatalogUnavailableException("No se pudo consultar la agenda en el catálogo: " + e.getMessage(), e);
        }
    }
}
