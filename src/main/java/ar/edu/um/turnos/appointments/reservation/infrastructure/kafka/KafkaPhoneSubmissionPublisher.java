package ar.edu.um.turnos.appointments.reservation.infrastructure.kafka;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.ports.out.PhoneSubmissionPublisher;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publica AdditionalInformationSubmitted en catedra.turnos.telefono.{groupId}
 * (sección 15.5 de la referencia). La message key es el reservationProcessId, como pide la
 * cátedra, para que los mensajes de un mismo proceso queden en orden.
 */
@Component
public class KafkaPhoneSubmissionPublisher implements PhoneSubmissionPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, String> kafka;
    private final String topic;
    private final String groupId;
    private final JsonMapper json = JsonMapper.builder().build();

    public KafkaPhoneSubmissionPublisher(
        KafkaTemplate<String, String> kafka,
        @Value("${catedra.kafka.phone-topic:catedra.turnos.telefono.local}") String topic,
        @Value("${catedra.group-id:}") String groupId
    ) {
        this.kafka = kafka;
        this.topic = topic;
        this.groupId = groupId;
    }

    @Override
    public void publish(PhoneSubmission submission) {
        if (!StringUtils.hasText(groupId)) {
            throw new IllegalStateException("Falta configurar CATEDRA_GROUP_ID");
        }
        String message = json.writeValueAsString(
            new SubmittedMessage(
                submission.eventId(),
                "AdditionalInformationSubmitted",
                groupId,
                submission.reservationProcessId(),
                submission.holdId(),
                submission.requestEventId(),
                new Information(submission.phoneNumber()),
                submission.occurredAt().toString(),
                1
            )
        );
        try {
            // Se espera la confirmación de Kafka: sin ella no sabemos si el teléfono llegó
            kafka.send(topic, submission.reservationProcessId(), message).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatedraUnavailableException("Se interrumpió el envío del teléfono", e);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            throw new CatedraUnavailableException("No se pudo publicar el teléfono en Kafka: " + e.getMessage(), e);
        }
    }

    record SubmittedMessage(
        String eventId,
        String eventType,
        String groupId,
        String reservationProcessId,
        String holdId,
        String requestEventId,
        Information information,
        String occurredAt,
        int schemaVersion
    ) {}

    record Information(String phoneNumber) {}
}
