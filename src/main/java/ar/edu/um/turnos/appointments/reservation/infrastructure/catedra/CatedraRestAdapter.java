package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Ocupaciones de la agenda, consultadas a la API de la cátedra con el JWT técnico.
 */
@Component
public class CatedraRestAdapter implements OccupancySource {

    private final CatedraProperties properties;
    private final RestClient restClient;

    public CatedraRestAdapter(CatedraProperties properties) {
        this.properties = properties;
        this.restClient = properties.restClient();
    }

    @Override
    public List<Occupancy> occupancies(long professionalExternalId, LocalDate from, LocalDate to) {
        properties.requireConfigured();
        try {
            OccupanciesResponse response = restClient
                .get()
                .uri("/api/appointment-occupancies?professionalId={id}&from={from}&to={to}", professionalExternalId, from, to)
                .header(HttpHeaders.AUTHORIZATION, properties.authorization())
                .retrieve()
                .body(OccupanciesResponse.class);
            return response == null ? List.of() : response.toDomain();
        } catch (RestClientException e) {
            throw new CatedraUnavailableException("No se pudieron consultar las ocupaciones en la cátedra: " + e.getMessage(), e);
        }
    }
}
