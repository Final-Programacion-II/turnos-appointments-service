package ar.edu.um.turnos.appointments.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.turnos.appointments.IntegrationTest;
import ar.edu.um.turnos.appointments.config.Constants;
import ar.edu.um.turnos.appointments.domain.User;
import ar.edu.um.turnos.appointments.repository.AuthorityRepository;
import ar.edu.um.turnos.appointments.repository.UserRepository;
import ar.edu.um.turnos.appointments.security.AuthoritiesConstants;
import ar.edu.um.turnos.appointments.service.dto.AccountUpdateDTO;
import ar.edu.um.turnos.appointments.service.dto.PasswordChangeDTO;
import ar.edu.um.turnos.appointments.service.dto.RegistrationDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro y cuenta del usuario por HTTP: {@link AccountResource}.
 */
@AutoConfigureMockMvc
@IntegrationTest
class AccountResourceIT {

    static final String TEST_USER_LOGIN = "test";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MockMvc restAccountMockMvc;

    private Long numberOfUsers;

    @BeforeEach
    void countUsers() {
        numberOfUsers = userRepository.count();
    }

    // Cada test borra los usuarios que creó: al terminar tiene que haber los mismos que al empezar
    @AfterEach
    void cleanupAndCheck() {
        assertThat(userRepository.count()).isEqualTo(numberOfUsers);
        numberOfUsers = null;
    }

    @Test
    @WithUnauthenticatedMockUser
    void testNonAuthenticatedUser() throws Exception {
        restAccountMockMvc.perform(get("/api/authenticate")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(TEST_USER_LOGIN)
    void testAuthenticatedUser() throws Exception {
        restAccountMockMvc.perform(get("/api/authenticate").with(request -> request)).andExpect(status().isNoContent());
    }

    @Test
    @Transactional
    @WithMockUser(TEST_USER_LOGIN)
    void testGetExistingAccount() throws Exception {
        User user = usuario(TEST_USER_LOGIN, "john.doe@jhipster.com");
        user.setFirstName("john");
        user.setLastName("doe");
        user.setImageUrl("http://placehold.it/50x50");
        user.setLangKey("en");
        user.setAuthorities(new HashSet<>(Set.of(authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow())));
        userRepository.saveAndFlush(user);

        restAccountMockMvc
            .perform(get("/api/account").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(user.getId().intValue()))
            .andExpect(jsonPath("$.login").value(TEST_USER_LOGIN))
            .andExpect(jsonPath("$.firstName").value("john"))
            .andExpect(jsonPath("$.lastName").value("doe"))
            .andExpect(jsonPath("$.email").value("john.doe@jhipster.com"))
            .andExpect(jsonPath("$.imageUrl").value("http://placehold.it/50x50"))
            .andExpect(jsonPath("$.langKey").value("en"))
            .andExpect(jsonPath("$.activated").value(true))
            .andExpect(jsonPath("$.authorities").value(AuthoritiesConstants.USER))
            // La contraseña no sale nunca, ni siquiera hasheada
            .andExpect(jsonPath("$.password").doesNotExist());

        borrar(TEST_USER_LOGIN);
    }

    @Test
    void testGetUnknownAccount() throws Exception {
        restAccountMockMvc.perform(get("/api/account").accept(MediaType.APPLICATION_PROBLEM_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    @Transactional
    void testRegisterValid() throws Exception {
        assertThat(userRepository.findOneByLogin("test-register-valid")).isEmpty();

        registrar(registro("test-register-valid", "password", "test-register-valid@example.com")).andExpect(status().isCreated());

        User registrado = userRepository.findOneByLogin("test-register-valid").orElseThrow();
        assertThat(registrado.isActivated()).isTrue();
        assertThat(registrado.getEmail()).isEqualTo("test-register-valid@example.com");

        borrar("test-register-valid");
    }

    static Stream<RegistrationDTO> invalidUsers() {
        return Stream.of(
            registro("funky-log(n", "password", "bob@example.com"), // login con un carácter que no se permite
            registro("bob", "password", "invalid"), // email inválido
            registro("bob", "123", "bob@example.com"), // contraseña de 3 caracteres
            registro("bob", null, "bob@example.com") // sin contraseña
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUsers")
    @Transactional
    void testRegisterInvalidUsers(RegistrationDTO invalidUser) throws Exception {
        registrar(invalidUser).andExpect(status().isBadRequest());

        assertThat(userRepository.findOneByLogin("bob")).isEmpty();
        assertThat(userRepository.findOneByEmailIgnoreCase("bob@example.com")).isEmpty();
    }

    // Los usuarios quedan activos al registrarse, así que un login o un email repetido se rechaza
    @Test
    @Transactional
    void testRegisterDuplicateLogin() throws Exception {
        registrar(registro("alice", "password", "alice@example.com")).andExpect(status().isCreated());

        registrar(registro("alice", "password", "alice2@example.com")).andExpect(status().isBadRequest());

        assertThat(userRepository.findOneByEmailIgnoreCase("alice2@example.com")).isEmpty();
        borrar("alice");
    }

    @Test
    @Transactional
    void testRegisterDuplicateEmail() throws Exception {
        registrar(registro("test-register-duplicate-email", "password", "test-register-duplicate-email@example.com")).andExpect(
            status().isCreated()
        );

        registrar(registro("test-register-duplicate-email-2", "password", "test-register-duplicate-email@example.com")).andExpect(
            status().isBadRequest()
        );

        // El email se compara sin distinguir mayúsculas
        registrar(registro("test-register-duplicate-email-3", "password", "TEST-register-duplicate-email@example.com")).andExpect(
            status().isBadRequest()
        );

        assertThat(userRepository.findOneByLogin("test-register-duplicate-email")).isPresent();
        assertThat(userRepository.findOneByLogin("test-register-duplicate-email-2")).isEmpty();
        assertThat(userRepository.findOneByLogin("test-register-duplicate-email-3")).isEmpty();
        borrar("test-register-duplicate-email");
    }

    // En JHipster, registrarse con el login de una cuenta desactivada la borraba y la reemplazaba.
    // Acá "desactivada" es "dada de baja": nadie puede quedarse con su login ni con su email.
    @Test
    @Transactional
    void testRegisterNoReemplazaUnaCuentaDesactivada() throws Exception {
        User baja = usuario("dado-de-baja", "dado-de-baja@example.com");
        baja.setActivated(false);
        userRepository.saveAndFlush(baja);

        registrar(registro("dado-de-baja", "password", "otro@example.com")).andExpect(status().isBadRequest());
        registrar(registro("otro-login", "password", "dado-de-baja@example.com")).andExpect(status().isBadRequest());

        User igual = userRepository.findOneByLogin("dado-de-baja").orElseThrow();
        assertThat(igual.getId()).isEqualTo(baja.getId());
        assertThat(igual.isActivated()).isFalse();
        assertThat(userRepository.findOneByLogin("otro-login")).isEmpty();

        borrar("dado-de-baja");
    }

    // El id, los roles y si queda activo los decide el backend, aunque el cliente los mande
    @Test
    @Transactional
    void testRegisterIgnoraLosCamposQueNoEligeElCliente() throws Exception {
        String pedido = """
            {
              "login": "badguy",
              "password": "password",
              "firstName": "Bad",
              "lastName": "Guy",
              "email": "badguy@example.com",
              "langKey": "es",
              "id": 999999,
              "activated": false,
              "authorities": ["ROLE_ADMIN"]
            }
            """;

        restAccountMockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(pedido))
            .andExpect(status().isCreated());

        User registrado = userRepository.findOneWithAuthoritiesByLogin("badguy").orElseThrow();
        assertThat(registrado.getId()).isNotEqualTo(999999L);
        assertThat(registrado.isActivated()).isTrue();
        assertThat(registrado.getAuthorities())
            .hasSize(1)
            .containsExactly(authorityRepository.findById(AuthoritiesConstants.USER).orElseThrow());

        borrar("badguy");
    }

    @Test
    @Transactional
    @WithMockUser("save-account")
    void testSaveAccount() throws Exception {
        User user = usuario("save-account", "save-account@example.com");
        userRepository.saveAndFlush(user);

        AccountUpdateDTO update = new AccountUpdateDTO(
            "firstname",
            "lastname",
            "save-account@example.com",
            Constants.DEFAULT_LANGUAGE,
            "http://placehold.it/50x50"
        );

        guardarCuenta(update).andExpect(status().isOk());

        User updatedUser = userRepository.findOneWithAuthoritiesByLogin("save-account").orElseThrow();
        assertThat(updatedUser.getFirstName()).isEqualTo("firstname");
        assertThat(updatedUser.getLastName()).isEqualTo("lastname");
        assertThat(updatedUser.getEmail()).isEqualTo("save-account@example.com");
        assertThat(updatedUser.getLangKey()).isEqualTo(Constants.DEFAULT_LANGUAGE);
        assertThat(updatedUser.getImageUrl()).isEqualTo("http://placehold.it/50x50");
        // Lo que no se cambia por acá queda igual
        assertThat(updatedUser.getPassword()).isEqualTo(user.getPassword());
        assertThat(updatedUser.isActivated()).isTrue();
        assertThat(updatedUser.getAuthorities()).isEmpty();

        borrar("save-account");
    }

    @Test
    @Transactional
    @WithMockUser("save-invalid-email")
    void testSaveInvalidEmail() throws Exception {
        userRepository.saveAndFlush(usuario("save-invalid-email", "save-invalid-email@example.com"));

        guardarCuenta(new AccountUpdateDTO("firstname", "lastname", "invalid email", Constants.DEFAULT_LANGUAGE, null)).andExpect(
            status().isBadRequest()
        );

        assertThat(userRepository.findOneByEmailIgnoreCase("invalid email")).isNotPresent();

        borrar("save-invalid-email");
    }

    @Test
    @Transactional
    @WithMockUser("save-existing-email")
    void testSaveExistingEmail() throws Exception {
        userRepository.saveAndFlush(usuario("save-existing-email", "save-existing-email@example.com"));
        userRepository.saveAndFlush(usuario("save-existing-email2", "save-existing-email2@example.com"));

        guardarCuenta(
            new AccountUpdateDTO("firstname", "lastname", "save-existing-email2@example.com", Constants.DEFAULT_LANGUAGE, null)
        ).andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByLogin("save-existing-email").orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("save-existing-email@example.com");

        borrar("save-existing-email");
        borrar("save-existing-email2");
    }

    @Test
    @Transactional
    @WithMockUser("save-existing-email-and-login")
    void testSaveExistingEmailAndLogin() throws Exception {
        userRepository.saveAndFlush(usuario("save-existing-email-and-login", "save-existing-email-and-login@example.com"));

        guardarCuenta(
            new AccountUpdateDTO("firstname", "lastname", "save-existing-email-and-login@example.com", Constants.DEFAULT_LANGUAGE, null)
        ).andExpect(status().isOk());

        User updatedUser = userRepository.findOneByLogin("save-existing-email-and-login").orElseThrow();
        assertThat(updatedUser.getEmail()).isEqualTo("save-existing-email-and-login@example.com");

        borrar("save-existing-email-and-login");
    }

    @Test
    @Transactional
    @WithMockUser("change-password-wrong-existing-password")
    void testChangePasswordWrongExistingPassword() throws Exception {
        String currentPassword = RandomStringUtils.insecure().nextAlphanumeric(60);
        usuarioConContrasenia("change-password-wrong-existing-password", currentPassword);

        cambiarContrasenia(new PasswordChangeDTO("1" + currentPassword, "new password")).andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByLogin("change-password-wrong-existing-password").orElseThrow();
        assertThat(passwordEncoder.matches("new password", updatedUser.getPassword())).isFalse();
        assertThat(passwordEncoder.matches(currentPassword, updatedUser.getPassword())).isTrue();

        borrar("change-password-wrong-existing-password");
    }

    @Test
    @Transactional
    @WithMockUser("change-password")
    void testChangePassword() throws Exception {
        String currentPassword = RandomStringUtils.insecure().nextAlphanumeric(60);
        usuarioConContrasenia("change-password", currentPassword);

        cambiarContrasenia(new PasswordChangeDTO(currentPassword, "new password")).andExpect(status().isOk());

        User updatedUser = userRepository.findOneByLogin("change-password").orElseThrow();
        assertThat(passwordEncoder.matches("new password", updatedUser.getPassword())).isTrue();

        borrar("change-password");
    }

    static Stream<String> invalidNewPasswords() {
        return Stream.of(
            RandomStringUtils.insecure().nextAlphanumeric(RegistrationDTO.PASSWORD_MIN_LENGTH - 1), // muy corta
            RandomStringUtils.insecure().nextAlphanumeric(RegistrationDTO.PASSWORD_MAX_LENGTH + 1), // muy larga
            "" // vacía
        );
    }

    @ParameterizedTest
    @MethodSource("invalidNewPasswords")
    @Transactional
    @WithMockUser("change-password-invalid")
    void testChangePasswordInvalidNewPassword(String newPassword) throws Exception {
        String currentPassword = RandomStringUtils.insecure().nextAlphanumeric(60);
        User user = usuarioConContrasenia("change-password-invalid", currentPassword);

        cambiarContrasenia(new PasswordChangeDTO(currentPassword, newPassword)).andExpect(status().isBadRequest());

        User updatedUser = userRepository.findOneByLogin("change-password-invalid").orElseThrow();
        assertThat(updatedUser.getPassword()).isEqualTo(user.getPassword());

        borrar("change-password-invalid");
    }

    private static RegistrationDTO registro(String login, String password, String email) {
        return new RegistrationDTO(login, password, "Alice", "Test", email, "http://placehold.it/50x50", Constants.DEFAULT_LANGUAGE);
    }

    private ResultActions registrar(RegistrationDTO registration) throws Exception {
        return restAccountMockMvc.perform(
            post("/api/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(registration))
        );
    }

    private ResultActions guardarCuenta(AccountUpdateDTO update) throws Exception {
        return restAccountMockMvc.perform(
            post("/api/account").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(update))
        );
    }

    private ResultActions cambiarContrasenia(PasswordChangeDTO change) throws Exception {
        return restAccountMockMvc.perform(
            post("/api/account/change-password").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(change))
        );
    }

    /**
     * Un usuario activo, sin guardar.
     */
    private static User usuario(String login, String email) {
        User user = new User();
        user.setLogin(login);
        user.setEmail(email);
        user.setPassword(RandomStringUtils.insecure().nextAlphanumeric(60));
        user.setActivated(true);
        return user;
    }

    private User usuarioConContrasenia(String login, String password) {
        User user = usuario(login, login + "@example.com");
        user.setPassword(passwordEncoder.encode(password));
        return userRepository.saveAndFlush(user);
    }

    private void borrar(String login) {
        userRepository.findOneByLogin(login).ifPresent(userRepository::delete);
        userRepository.flush();
    }
}
