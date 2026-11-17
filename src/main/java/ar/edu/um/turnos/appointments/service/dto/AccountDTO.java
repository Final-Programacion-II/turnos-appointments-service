package ar.edu.um.turnos.appointments.service.dto;

import ar.edu.um.turnos.appointments.domain.Authority;
import ar.edu.um.turnos.appointments.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La cuenta del usuario autenticado, con los mismos campos que devuelve JHipster en
 * {@code GET /api/account}. Nunca incluye la contraseña.
 */
public record AccountDTO(
    Long id,
    String login,
    String firstName,
    String lastName,
    String email,
    String imageUrl,
    boolean activated,
    String langKey,
    String createdBy,
    Instant createdDate,
    String lastModifiedBy,
    Instant lastModifiedDate,
    Set<String> authorities
) {
    public static AccountDTO from(User user) {
        return new AccountDTO(
            user.getId(),
            user.getLogin(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getImageUrl(),
            user.isActivated(),
            user.getLangKey(),
            user.getCreatedBy(),
            user.getCreatedDate(),
            user.getLastModifiedBy(),
            user.getLastModifiedDate(),
            user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet())
        );
    }
}
