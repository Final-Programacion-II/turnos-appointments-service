package ar.edu.um.turnos.appointments.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PhoneNumberTest {

    @Test
    void sacaEspaciosGuionesParentesisYPuntos() {
        assertThat(PhoneNumber.of("+54 9 (261) 555-1234").value()).isEqualTo("+5492615551234");
        assertThat(PhoneNumber.of("261.555.1234").value()).isEqualTo("2615551234");
    }

    @Test
    void aceptaDe7a15Digitos() {
        assertThat(PhoneNumber.of("1234567").value()).isEqualTo("1234567");
        assertThat(PhoneNumber.of("+123456789012345").value()).isEqualTo("+123456789012345");
    }

    @Test
    void rechazaLosQueLaCatedraRechazaria() {
        assertThatThrownBy(() -> PhoneNumber.of("123456")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.of("1234567890123456")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.of("261-CASA-12")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.of("54+2615551234")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThatThrownBy(() -> PhoneNumber.of(null)).isInstanceOf(InvalidPhoneNumberException.class);
    }
}
