package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Hold y confirmación inicial en la API de la cátedra (secciones 9 y 10 de la referencia).
 *
 * Cómo se traducen las respuestas:
 * - 4xx con code (SLOT_ALREADY_HELD, HOLD_EXPIRED, ...): CatedraRejectedException.
 * - 401 o 403: el problema es nuestra cuenta técnica (token vencido o mal configurado), no el
 *   pedido del usuario. Se registra en el log y se trata como cátedra no disponible.
 * - 5xx, timeout o sin conexión: CatedraUnavailableException.
 */
@Component
public class CatedraHoldAdapter implements CatedraReservations {

    private static final Logger LOG = LoggerFactory.getLogger(CatedraHoldAdapter.class);
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
        HoldResponse response = call("crear el hold", () ->
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
        ConfirmResponse response = call("confirmar el hold", () ->
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

    private <T> T call(String operation, Supplier<T> request) {
        properties.requireConfigured();
        T response;
        try {
            response = request.get();
        } catch (HttpClientErrorException e) {
            throw translate(operation, e);
        } catch (RestClientException e) {
            throw new CatedraUnavailableException("No se pudo " + operation + " en la cátedra: " + e.getMessage(), e);
        }
        if (response == null) {
            throw new CatedraUnavailableException("La cátedra respondió sin datos al " + operation, null);
        }
        return response;
    }

    private static RuntimeException translate(String operation, HttpClientErrorException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            LOG.error("La cátedra rechazó la cuenta técnica al {} ({}). Revisar CATEDRA_TOKEN.", operation, status);
            return new CatedraUnavailableException("La cátedra rechazó la cuenta técnica (" + status + ")", e);
        }
        Problem problem = readProblem(e);
        String code = problem != null && problem.code() != null ? problem.code() : "CATEDRA_REJECTED";
        return new CatedraRejectedException(status, code, problem == null ? null : problem.detail());
    }

    private static Problem readProblem(HttpClientErrorException e) {
        try {
            return e.getResponseBodyAs(Problem.class);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    record HoldRequest(long professionalId, String date, String startTime, String externalPatientId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record HoldResponse(String holdId, String reservationProcessId, String status, Instant expiresAt) {}

    record ConfirmRequest(String externalPatientId, String patientFirstName, String patientLastName) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConfirmResponse(String reservationProcessId, String status, Instant expiresAt) {}

    /** Error de la cátedra (application/problem+json): se decide por el code, nunca por el texto. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Problem(String code, String detail) {}
}
