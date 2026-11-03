package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * La API de la cátedra no respondió o respondió con un error inesperado.
 */
public class CatedraUnavailableException extends RuntimeException {

    public CatedraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
