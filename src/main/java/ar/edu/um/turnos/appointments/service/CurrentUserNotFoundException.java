package ar.edu.um.turnos.appointments.service;

import java.io.Serial;

/**
 * El token es válido pero su usuario ya no está en la base (por ejemplo, se borró después de
 * emitir el token).
 */
public class CurrentUserNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public CurrentUserNotFoundException() {
        super("No se encontró el usuario autenticado");
    }
}
