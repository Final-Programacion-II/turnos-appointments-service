package ar.edu.um.turnos.appointments.reservation.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.ports.out.PhoneSubmissionPublisher.PhoneSubmission;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class KafkaPhoneSubmissionPublisherTest {

    private static final PhoneSubmission ENVIO = new PhoneSubmission(
        "phone-submitted-b91e",
        "process-a92b47d0",
        "hold-6c0c2f41",
        "request-event-8a31",
        "+5492615551234",
        Instant.parse("2026-09-10T14:36:00Z")
    );

    private KafkaTemplate<String, String> kafka;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void preparar() {
        kafka = mock(KafkaTemplate.class);
    }

    @Test
    void publicaElMensajeConElFormatoDeLaReferencia() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));

        publisher("proyecto-juan-perez").publish(ENVIO);

        ArgumentCaptor<String> mensaje = ArgumentCaptor.forClass(String.class);
        // Topic de teléfono y, como message key, el reservationProcessId
        verify(kafka).send(eq("catedra.turnos.telefono.proyecto-juan-perez"), eq("process-a92b47d0"), mensaje.capture());

        JsonNode json = JsonMapper.builder().build().readTree(mensaje.getValue());
        assertThat(json.get("eventId").asString()).isEqualTo("phone-submitted-b91e");
        assertThat(json.get("eventType").asString()).isEqualTo("AdditionalInformationSubmitted");
        assertThat(json.get("groupId").asString()).isEqualTo("proyecto-juan-perez");
        assertThat(json.get("reservationProcessId").asString()).isEqualTo("process-a92b47d0");
        assertThat(json.get("holdId").asString()).isEqualTo("hold-6c0c2f41");
        assertThat(json.get("requestEventId").asString()).isEqualTo("request-event-8a31");
        assertThat(json.get("information").get("phoneNumber").asString()).isEqualTo("+5492615551234");
        assertThat(json.get("occurredAt").asString()).isEqualTo("2026-09-10T14:36:00Z");
        assertThat(json.get("schemaVersion").asInt()).isEqualTo(1);
    }

    @Test
    void siKafkaNoConfirmaElEnvioFalla() {
        CompletableFuture<SendResult<String, String>> fallido = CompletableFuture.failedFuture(new IllegalStateException("broker caído"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(fallido);

        assertThatThrownBy(() -> publisher("proyecto-juan-perez").publish(ENVIO)).isInstanceOf(CatedraUnavailableException.class);
    }

    @Test
    void sinGroupIdNoPublica() {
        assertThatThrownBy(() -> publisher("").publish(ENVIO))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CATEDRA_GROUP_ID");
    }

    private KafkaPhoneSubmissionPublisher publisher(String groupId) {
        return new KafkaPhoneSubmissionPublisher(kafka, "catedra.turnos.telefono.proyecto-juan-perez", groupId);
    }
}
