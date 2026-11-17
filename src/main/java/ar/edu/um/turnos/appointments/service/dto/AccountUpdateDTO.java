package ar.edu.um.turnos.appointments.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Lo que un usuario puede cambiar de su propia cuenta. El login, los roles y si está activo no
 * se cambian por acá.
 */
public record AccountUpdateDTO(
    @Size(max = 50) String firstName,
    @Size(max = 50) String lastName,
    @Email @Size(min = 5, max = 254) String email,
    @Size(min = 2, max = 10) String langKey,
    @Size(max = 256) String imageUrl
) {}
