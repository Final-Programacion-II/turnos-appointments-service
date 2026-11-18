package ar.edu.um.turnos.appointments.service;

import ar.edu.um.turnos.appointments.repository.AuthorityRepository;
import ar.edu.um.turnos.appointments.repository.UserRepository;
import ar.edu.um.turnos.appointments.repository.entity.Authority;
import ar.edu.um.turnos.appointments.repository.entity.User;
import ar.edu.um.turnos.appointments.security.AuthoritiesConstants;
import ar.edu.um.turnos.appointments.security.SecurityUtils;
import ar.edu.um.turnos.appointments.service.dto.AccountUpdateDTO;
import ar.edu.um.turnos.appointments.service.dto.RegistrationDTO;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Usuarios finales de la aplicación: registro, datos de la propia cuenta y cambio de
 * contraseña. No hay administración de usuarios de otros.
 */
@Service
@Transactional
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthorityRepository authorityRepository;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuthorityRepository authorityRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authorityRepository = authorityRepository;
    }

    /**
     * Registra un usuario. Queda activo enseguida y con el rol USER: ninguna de las dos cosas la
     * elige el cliente.
     *
     * Un login o un email que ya existen se rechazan siempre, también si esa cuenta está
     * desactivada. JHipster borraba la cuenta desactivada y la reemplazaba por la nueva; acá una
     * cuenta desactivada es una cuenta dada de baja, y nadie tiene que poder quedarse con su login.
     */
    public User registerUser(RegistrationDTO registration) {
        String login = registration.login().toLowerCase(Locale.ROOT);
        if (userRepository.findOneByLogin(login).isPresent()) {
            throw new UsernameAlreadyUsedException();
        }
        if (registration.email() != null && userRepository.findOneByEmailIgnoreCase(registration.email()).isPresent()) {
            throw new EmailAlreadyUsedException();
        }

        User newUser = new User();
        newUser.setLogin(login);
        newUser.setPassword(passwordEncoder.encode(registration.password()));
        newUser.setFirstName(registration.firstName());
        newUser.setLastName(registration.lastName());
        if (registration.email() != null) {
            newUser.setEmail(registration.email().toLowerCase(Locale.ROOT));
        }
        newUser.setImageUrl(registration.imageUrl());
        newUser.setLangKey(registration.langKey());
        // Queda activo al registrarse: no hay verificación por correo (lo pide el enunciado)
        newUser.setActivated(true);
        Set<Authority> authorities = new HashSet<>();
        authorityRepository.findById(AuthoritiesConstants.USER).ifPresent(authorities::add);
        newUser.setAuthorities(authorities);
        userRepository.save(newUser);
        LOG.debug("Usuario registrado: {}", newUser);
        return newUser;
    }

    /**
     * Cambia los datos de la cuenta del usuario autenticado.
     *
     * @throws EmailAlreadyUsedException si el email nuevo ya es de otro usuario.
     */
    public void updateAccount(AccountUpdateDTO update) {
        User user = currentUser();
        if (update.email() != null) {
            Optional<User> sameEmail = userRepository.findOneByEmailIgnoreCase(update.email());
            if (sameEmail.isPresent() && !sameEmail.orElseThrow().getLogin().equalsIgnoreCase(user.getLogin())) {
                throw new EmailAlreadyUsedException();
            }
            user.setEmail(update.email().toLowerCase(Locale.ROOT));
        }
        user.setFirstName(update.firstName());
        user.setLastName(update.lastName());
        user.setLangKey(update.langKey());
        user.setImageUrl(update.imageUrl());
        userRepository.save(user);
        LOG.debug("Cuenta actualizada: {}", user);
    }

    /**
     * @throws InvalidPasswordException si la contraseña actual no coincide.
     */
    public void changePassword(String currentClearTextPassword, String newPassword) {
        User user = currentUser();
        if (!passwordEncoder.matches(currentClearTextPassword, user.getPassword())) {
            throw new InvalidPasswordException();
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        LOG.debug("Contraseña cambiada: {}", user);
    }

    @Transactional(readOnly = true)
    public Optional<User> getUserWithAuthorities() {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneWithAuthoritiesByLogin);
    }

    private User currentUser() {
        return SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).orElseThrow(CurrentUserNotFoundException::new);
    }
}
