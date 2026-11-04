package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraAppointment;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraAppointments;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Listado de reservas de la cuenta técnica (sección 11 de la referencia), filtrado por paciente.
 *
 * No se usan los filtros from/to: la cátedra responde 500 cuando se mandan (probado el 3/11).
 * La respuesta viene paginada: se piden páginas hasta que una venga incompleta, con un tope
 * para no quedar en un ciclo si la cátedra respondiera mal.
 */
@Component
public class CatedraAppointmentsAdapter implements CatedraAppointments {

    private static final Logger LOG = LoggerFactory.getLogger(CatedraAppointmentsAdapter.class);
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 10;

    private final CatedraProperties properties;
    private final RestClient restClient;

    public CatedraAppointmentsAdapter(CatedraProperties properties) {
        this.properties = properties;
        this.restClient = properties.restClient();
    }

    @Override
    public List<CatedraAppointment> appointmentsOf(String externalPatientId) {
        List<CatedraAppointment> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            int current = page;
            List<AppointmentJson> items = CatedraHttp.call(properties, "listar las reservas", () ->
                restClient
                    .get()
                    .uri("/api/appointments?externalPatientId={patient}&page={page}&size={size}", externalPatientId, current, PAGE_SIZE)
                    .header(HttpHeaders.AUTHORIZATION, properties.authorization())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AppointmentJson>>() {})
            );
            items.stream().map(CatedraAppointmentsAdapter::toDomain).filter(Objects::nonNull).forEach(all::add);
            if (items.size() < PAGE_SIZE) {
                return all;
            }
        }
        LOG.warn("El paciente {} tiene más de {} reservas: se revisan solo las primeras", externalPatientId, PAGE_SIZE * MAX_PAGES);
        return all;
    }

    /**
     * Un estado que no conocemos se descarta: mejor no tocar el proceso que adivinar.
     */
    private static CatedraAppointment toDomain(AppointmentJson json) {
        if (json.reservationProcessId() == null || json.status() == null) {
            return null;
        }
        try {
            CatedraAppointment.Status status = CatedraAppointment.Status.valueOf(json.status());
            return new CatedraAppointment(json.reservationProcessId(), status, json.reservationId(), json.cancellationReason());
        } catch (IllegalArgumentException unknown) {
            LOG.warn("Estado de reserva desconocido en la cátedra: {}", json.status());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AppointmentJson(String reservationProcessId, String status, Long reservationId, String cancellationReason) {}
}
