package ar.edu.um.turnos.appointments.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.turnos.appointments.IntegrationTest;
import ar.edu.um.turnos.appointments.config.Constants;
import ar.edu.um.turnos.appointments.domain.User;
import ar.edu.um.turnos.appointments.repository.UserRepository;
import ar.edu.um.turnos.appointments.web.rest.vm.LoginVM;
import ar.edu.um.turnos.appointments.web.rest.vm.ManagedUserVM;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@AutoConfigureMockMvc
@IntegrationTest
class PasswordHashingIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void unUsuarioNuevoQuedaConArgon2idYPuedeEntrarEnseguida() throws Exception {
        ManagedUserVM nuevo = new ManagedUserVM();
        nuevo.setLogin("paciente-argon");
        nuevo.setPassword("clave-segura");
        nuevo.setFirstName("Juan");
        nuevo.setLastName("Perez");
        nuevo.setEmail("paciente-argon@example.com");
        nuevo.setLangKey(Constants.DEFAULT_LANGUAGE);

        mockMvc
            .perform(post("/api/register").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(nuevo)))
            .andExpect(status().isCreated());

        User guardado = userRepository.findOneByLogin("paciente-argon").orElseThrow();
        assertThat(guardado.getPassword()).startsWith("{argon2}$argon2id$");
        assertThat(guardado.isActivated()).isTrue();

        // Sin mail de activación: puede iniciar sesión apenas se registra
        login("paciente-argon", "clave-segura");
    }

    @Test
    @Transactional
    void unHashBcryptViejoSigueAndandoYSePasaAArgon2idAlEntrar() throws Exception {
        // Como los usuarios de ejemplo de JHipster: hash bcrypt sin prefijo
        User viejo = new User();
        viejo.setLogin("usuario-bcrypt");
        viejo.setEmail("usuario-bcrypt@example.com");
        viejo.setActivated(true);
        viejo.setPassword(new BCryptPasswordEncoder().encode("clave-vieja"));
        userRepository.saveAndFlush(viejo);

        login("usuario-bcrypt", "clave-vieja");

        assertThat(userRepository.findOneByLogin("usuario-bcrypt").orElseThrow().getPassword()).startsWith("{argon2}$argon2id$");

        // Con el hash nuevo sigue entrando
        login("usuario-bcrypt", "clave-vieja");
    }

    private void login(String usuario, String clave) throws Exception {
        LoginVM login = new LoginVM();
        login.setUsername(usuario);
        login.setPassword(clave);
        mockMvc
            .perform(post("/api/authenticate").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(login)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id_token").isNotEmpty());
    }
}
