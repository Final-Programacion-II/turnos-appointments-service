package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Hold y confirmación inicial en la API de la cátedra (secciones 9 y 10 de la referencia).
 * Los errores se traducen en {@link CatedraHttp}.
 */
@Component
public class CatedraHoldAdapter implements CatedraReservations {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final CatedraProperties properties;
    private final RestClient restClient;

    public CatedraHoldAdapter(CatedraProperties properties) {
        this.properties = properties;
        this.restClient = properties.restClient();
    }

    @Override
    public Hold createHold(long professionalExternalId, LocalDate date, LocalTime startTime, String externalPatientId) {
        // Fecha y hora van como texto, con el formato exacto que pide la referencia
        HoldRequest body = new HoldRequest(professionalExternalId, date.toString(), startTime.format(HOUR), externalPatientId);
        HoldResponse response = CatedraHttp.call(properties, "crear el hold", () ->
            restClient
                .post()
                .uri("/api/appointment-holds")
                .header(HttpHeaders.AUTHORIZATION, properties.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(HoldResponse.class)
        );
        return new Hold(response.holdId(), response.reservationProcessId(), response.expiresAt());
    }

    @Override
    public Instant confirmHold(String holdId, Patient patient) {
        ConfirmRequest body = new ConfirmRequest(patient.externalPatientId(), patient.firstName(), patient.lastName());
        ConfirmResponse response = CatedraHttp.call(properties, "confirmar el hold", () ->
            restClient
                .post()
                .uri("/api/appointment-holds/{holdId}/confirm", holdId)
                .header(HttpHeaders.AUTHORIZATION, properties.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ConfirmResponse.class)
        );
        return response.expiresAt();
    }

    record HoldRequest(long professionalId, String date, String startTime, String externalPatientId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HoldResponse(String holdId, String reservationProcessId, String status, Instant expiresAt) {}

    record ConfirmRequest(String externalPatientId, String patientFirstName, String patientLastName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConfirmResponse(String reservationProcessId, String status, Instant expiresAt) {}
}
