package ar.edu.um.turnos.appointments.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.IntegrationTest;
import ar.edu.um.turnos.appointments.repository.UserRepository;
import ar.edu.um.turnos.appointments.repository.entity.Authority;
import ar.edu.um.turnos.appointments.repository.entity.User;
import ar.edu.um.turnos.appointments.security.AuthoritiesConstants;
import ar.edu.um.turnos.appointments.service.dto.AccountUpdateDTO;
import ar.edu.um.turnos.appointments.service.dto.RegistrationDTO;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro, datos de la cuenta y cambio de contraseña, contra la base real.
 * Cada test corre en una transacción que se deshace al terminar.
 */
@IntegrationTest
@Transactional
class UserServiceIT {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registraUnUsuarioActivoConRolUserYLaContraseniaHasheada() {
        User nuevo = userService.registerUser(registro("Ana.Servicio", "Ana.Servicio@Example.com"));

        User guardado = userRepository.findOneWithAuthoritiesByLogin("ana.servicio").orElseThrow();
        assertThat(guardado.getId()).isEqualTo(nuevo.getId());
        // El login y el email se guardan en minúsculas
        assertThat(guardado.getEmail()).isEqualTo("ana.servicio@example.com");
        assertThat(guardado.isActivated()).isTrue();
        assertThat(guardado.getAuthorities()).extracting(Authority::getName).containsExactly(AuthoritiesConstants.USER);
        assertThat(guardado.getPassword()).isNotEqualTo("clave-segura");
        assertThat(passwordEncoder.matches("clave-segura", guardado.getPassword())).isTrue();
    }

    @Test
    void rechazaUnLoginRepetido() {
        userService.registerUser(registro("ana.servicio", "ana.servicio@example.com"));

        assertThatThrownBy(() -> userService.registerUser(registro("ANA.servicio", "otra@example.com"))).isInstanceOf(
            UsernameAlreadyUsedException.class
        );
    }

    @Test
    void rechazaUnEmailRepetidoSinImportarMayusculas() {
        userService.registerUser(registro("ana.servicio", "ana.servicio@example.com"));

        assertThatThrownBy(() -> userService.registerUser(registro("otra.servicio", "ANA.servicio@example.com"))).isInstanceOf(
            EmailAlreadyUsedException.class
        );
    }

    @Test
    void registrarseConElLoginDeUnaCuentaDesactivadaNoLaReemplaza() {
        User baja = usuarioGuardado("dada.de.baja", "dada.de.baja@example.com", false);

        assertThatThrownBy(() -> userService.registerUser(registro("dada.de.baja", "nueva@example.com"))).isInstanceOf(
            UsernameAlreadyUsedException.class
        );
        assertThatThrownBy(() -> userService.registerUser(registro("nueva", "dada.de.baja@example.com"))).isInstanceOf(
            EmailAlreadyUsedException.class
        );

        User igual = userRepository.findOneByLogin("dada.de.baja").orElseThrow();
        assertThat(igual.getId()).isEqualTo(baja.getId());
        assertThat(igual.isActivated()).isFalse();
        assertThat(igual.getEmail()).isEqualTo("dada.de.baja@example.com");
    }

    @Test
    @WithMockUser("ana.servicio")
    void cambiaLosDatosDeLaPropiaCuenta() {
        usuarioGuardado("ana.servicio", "ana.servicio@example.com", true);

        userService.updateAccount(new AccountUpdateDTO("Ana", "Quiroga", "Ana.Nueva@Example.com", "es", "http://placehold.it/50x50"));

        User actualizado = userRepository.findOneByLogin("ana.servicio").orElseThrow();
        assertThat(actualizado.getFirstName()).isEqualTo("Ana");
        assertThat(actualizado.getLastName()).isEqualTo("Quiroga");
        assertThat(actualizado.getEmail()).isEqualTo("ana.nueva@example.com");
        assertThat(actualizado.getLangKey()).isEqualTo("es");
        assertThat(actualizado.getImageUrl()).isEqualTo("http://placehold.it/50x50");
    }

    @Test
    @WithMockUser("ana.servicio")
    void noDejaUsarElEmailDeOtroUsuario() {
        usuarioGuardado("ana.servicio", "ana.servicio@example.com", true);
        usuarioGuardado("bruno.servicio", "bruno.servicio@example.com", true);

        AccountUpdateDTO conEmailAjeno = new AccountUpdateDTO("Ana", "Quiroga", "BRUNO.servicio@example.com", "es", null);

        assertThatThrownBy(() -> userService.updateAccount(conEmailAjeno)).isInstanceOf(EmailAlreadyUsedException.class);
        assertThat(userRepository.findOneByLogin("ana.servicio").orElseThrow().getEmail()).isEqualTo("ana.servicio@example.com");
    }

    @Test
    @WithMockUser("ana.servicio")
    void cambiaLaContraseniaSoloSiLaActualCoincide() {
        User ana = usuarioGuardado("ana.servicio", "ana.servicio@example.com", true);
        ana.setPassword(passwordEncoder.encode("clave-actual"));
        userRepository.saveAndFlush(ana);

        assertThatThrownBy(() -> userService.changePassword("otra-clave", "clave-nueva")).isInstanceOf(InvalidPasswordException.class);
        assertThat(
            passwordEncoder.matches("clave-actual", userRepository.findOneByLogin("ana.servicio").orElseThrow().getPassword())
        ).isTrue();

        userService.changePassword("clave-actual", "clave-nueva");
        assertThat(
            passwordEncoder.matches("clave-nueva", userRepository.findOneByLogin("ana.servicio").orElseThrow().getPassword())
        ).isTrue();
    }

    @Test
    @WithMockUser("ya.no.existe")
    void siElUsuarioDelTokenYaNoExisteNoCambiaNada() {
        AccountUpdateDTO datos = new AccountUpdateDTO("Ana", "Quiroga", "ana@example.com", "es", null);

        assertThatThrownBy(() -> userService.updateAccount(datos)).isInstanceOf(CurrentUserNotFoundException.class);
        assertThatThrownBy(() -> userService.changePassword("a", "clave-nueva")).isInstanceOf(CurrentUserNotFoundException.class);
    }

    private static RegistrationDTO registro(String login, String email) {
        return new RegistrationDTO(login, "clave-segura", "Ana", "Quiroga", email, null, "es");
    }

    private User usuarioGuardado(String login, String email, boolean activo) {
        User user = new User();
        user.setLogin(login);
        user.setEmail(email);
        user.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        user.setActivated(activo);
        return userRepository.saveAndFlush(user);
    }
}
