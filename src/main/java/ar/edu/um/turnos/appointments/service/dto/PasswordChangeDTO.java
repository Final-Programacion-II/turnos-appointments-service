package ar.edu.um.turnos.appointments.service.dto;

/**
 * Cambio de contraseña: la actual y la nueva.
 */
public record PasswordChangeDTO(String currentPassword, String newPassword) {
    /**
     * No muestra las contraseñas: así no terminan en un log por accidente.
     */
    @Override
    public String toString() {
        return "PasswordChangeDTO{}";
    }
}
