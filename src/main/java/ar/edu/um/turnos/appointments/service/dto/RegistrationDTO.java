package ar.edu.um.turnos.appointments.service.dto;

import ar.edu.um.turnos.appointments.config.Constants;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para registrar un usuario. Son los campos y los límites del usuario de JHipster que
 * pide el enunciado. El id, si queda activo y los roles los decide el backend: aunque el
 * cliente los mande en el pedido, se ignoran.
 */
public record RegistrationDTO(
    @NotBlank @Pattern(regexp = Constants.LOGIN_REGEX) @Size(min = 1, max = 50) String login,
    @Size(min = PASSWORD_MIN_LENGTH, max = PASSWORD_MAX_LENGTH) String password,
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @Size(min = 5, max = 254) String email,
    @Size(max = 256) String imageUrl,
    @Size(min = 2, max = 10) String langKey
) {
    public static final int PASSWORD_MIN_LENGTH = 4;
    public static final int PASSWORD_MAX_LENGTH = 100;

    /**
     * El largo se controla también acá (y no solo con la anotación) porque una contraseña null
     * pasa la validación de {@code @Size}.
     */
    public static boolean isPasswordLengthInvalid(String password) {
        return password == null || password.length() < PASSWORD_MIN_LENGTH || password.length() > PASSWORD_MAX_LENGTH;
    }

    /**
     * No muestra la contraseña: así no termina en un log por accidente.
     */
    @Override
    public String toString() {
        return "RegistrationDTO{login='" + login + "', email='" + email + "'}";
    }
}
