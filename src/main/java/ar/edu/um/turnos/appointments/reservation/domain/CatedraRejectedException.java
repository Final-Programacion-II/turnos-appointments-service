package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * La cátedra respondió y rechazó la operación con un código de negocio, por ejemplo
 * SLOT_ALREADY_HELD (409) o HOLD_EXPIRED (404). Reintentar igual no sirve.
 */
public class CatedraRejectedException extends RuntimeException {

    private final int httpStatus;
    private final String code;

    public CatedraRejectedException(int httpStatus, String code, String detail) {
        super(code + (detail == null ? "" : ": " + detail));
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String code() {
        return code;
    }
}
