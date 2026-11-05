package ar.edu.um.turnos.appointments.reservation.infrastructure.catedra;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Cómo se traducen las respuestas de la API de la cátedra, igual para todos los adaptadores:
 * - 4xx con code (SLOT_ALREADY_HELD, HOLD_EXPIRED, ...): CatedraRejectedException.
 * - 401 o 403: el problema es nuestra cuenta técnica (token vencido o mal configurado), no el
 *   pedido del usuario. Se registra en el log y se trata como cátedra no disponible.
 * - 5xx, timeout o sin conexión: CatedraUnavailableException.
 */
final class CatedraHttp {

    private static final Logger LOG = LoggerFactory.getLogger(CatedraHttp.class);

    private CatedraHttp() {}

    static <T> T call(CatedraProperties properties, String operation, Supplier<T> request) {
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

    /** Error de la cátedra (application/problem+json): se decide por el code, nunca por el texto. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Problem(String code, String detail) {}
}
