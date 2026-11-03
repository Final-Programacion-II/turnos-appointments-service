package ar.edu.um.turnos.appointments.reservation.infrastructure.web;

import ar.edu.um.turnos.appointments.reservation.domain.CatalogUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.InvalidPhoneNumberException;
import ar.edu.um.turnos.appointments.reservation.domain.InvalidReservationStateException;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.SlotNotAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del módulo de reservas a respuestas HTTP con un código estable, como
 * hace la cátedra: el cliente decide por status y code, no por el texto.
 * Va antes que el ExceptionTranslator de JHipster, que convertiría todo en 500.
 */
@RestControllerAdvice(basePackageClasses = ReservationExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReservationExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ReservationExceptionHandler.class);

    @ExceptionHandler(ProfessionalNotFoundException.class)
    ProblemDetail professionalNotFound(ProfessionalNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    ProblemDetail reservationNotFound(ReservationNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "RESERVATION_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(SlotNotAvailableException.class)
    ProblemDetail slotNotAvailable(SlotNotAvailableException e) {
        return problem(HttpStatus.CONFLICT, "SLOT_NOT_AVAILABLE", e.getMessage());
    }

    @ExceptionHandler(InvalidPhoneNumberException.class)
    ProblemDetail invalidPhone(InvalidPhoneNumberException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_PHONE_NUMBER", e.getMessage());
    }

    @ExceptionHandler(InvalidReservationStateException.class)
    ProblemDetail invalidState(InvalidReservationStateException e) {
        return problem(HttpStatus.CONFLICT, "INVALID_RESERVATION_STATE", e.getMessage());
    }

    /**
     * La cátedra rechazó el pedido (por ejemplo, SLOT_ALREADY_HELD): se devuelve el mismo status y
     * el mismo code, para que la app pueda explicar qué pasó.
     */
    @ExceptionHandler(CatedraRejectedException.class)
    ProblemDetail catedraRejected(CatedraRejectedException e) {
        HttpStatus status = HttpStatus.resolve(e.httpStatus());
        return problem(status == null ? HttpStatus.CONFLICT : status, e.code(), "El servicio central rechazó el pedido (" + e.code() + ")");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
    }

    @ExceptionHandler(CatalogUnavailableException.class)
    ProblemDetail catalogUnavailable(CatalogUnavailableException e) {
        LOG.warn("Catálogo no disponible: {}", e.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", "El catálogo no está disponible, probá de nuevo en un rato");
    }

    @ExceptionHandler(CatedraUnavailableException.class)
    ProblemDetail catedraUnavailable(CatedraUnavailableException e) {
        LOG.warn("Cátedra no disponible: {}", e.getMessage());
        return problem(
            HttpStatus.SERVICE_UNAVAILABLE,
            "CATEDRA_UNAVAILABLE",
            "El servicio central no está disponible, probá de nuevo en un rato"
        );
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
