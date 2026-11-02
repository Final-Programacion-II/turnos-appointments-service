package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * El catálogo no respondió o respondió con un error. Sin el catálogo no se puede validar la
 * agenda vigente, así que no se puede empezar una operación que la necesite (sección 8 del enunciado).
 */
public class CatalogUnavailableException extends RuntimeException {

    public CatalogUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
