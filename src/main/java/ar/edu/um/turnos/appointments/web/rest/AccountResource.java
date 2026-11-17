package ar.edu.um.turnos.appointments.web.rest;

import ar.edu.um.turnos.appointments.service.UserService;
import ar.edu.um.turnos.appointments.service.dto.AccountDTO;
import ar.edu.um.turnos.appointments.service.dto.AccountUpdateDTO;
import ar.edu.um.turnos.appointments.service.dto.PasswordChangeDTO;
import ar.edu.um.turnos.appointments.service.dto.RegistrationDTO;
import ar.edu.um.turnos.appointments.web.rest.errors.InvalidPasswordException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registro y cuenta del usuario autenticado. Las rutas y los campos son los de JHipster, que es
 * el contrato de usuarios que pide el enunciado.
 */
@RestController
@RequestMapping("/api")
public class AccountResource {

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Account resource request invalid")
    private static class AccountResourceException extends RuntimeException {

        private AccountResourceException(String message) {
            super(message);
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(AccountResource.class);

    private final UserService userService;

    public AccountResource(UserService userService) {
        this.userService = userService;
    }

    /**
     * {@code POST /register}: registra un usuario. Es público.
     * Responde 201, o 400 si los datos no son válidos o el login o el email ya existen.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerAccount(@Valid @RequestBody RegistrationDTO registration) {
        LOG.debug("REST request to register account");
        if (RegistrationDTO.isPasswordLengthInvalid(registration.password())) {
            throw new InvalidPasswordException();
        }
        userService.registerUser(registration);
    }

    /**
     * {@code GET /account}: la cuenta del usuario del token.
     */
    @GetMapping("/account")
    public AccountDTO getAccount() {
        LOG.debug("REST request to get account");
        return userService
            .getUserWithAuthorities()
            .map(AccountDTO::from)
            .orElseThrow(() -> new AccountResourceException("User could not be found"));
    }

    /**
     * {@code POST /account}: cambia nombre, apellido, email, idioma e imagen del usuario del
     * token. Responde 400 si el email ya es de otro usuario.
     */
    @PostMapping("/account")
    public void saveAccount(@Valid @RequestBody AccountUpdateDTO update) {
        LOG.debug("REST request to save account");
        userService.updateAccount(update);
    }

    /**
     * {@code POST /account/change-password}: cambia la contraseña del usuario del token.
     * Responde 400 si la actual no coincide o la nueva no tiene un largo válido.
     */
    @PostMapping(path = "/account/change-password")
    public void changePassword(@RequestBody PasswordChangeDTO passwordChange) {
        LOG.debug("REST request to change password");
        if (RegistrationDTO.isPasswordLengthInvalid(passwordChange.newPassword())) {
            throw new InvalidPasswordException();
        }
        userService.changePassword(passwordChange.currentPassword(), passwordChange.newPassword());
    }
}
