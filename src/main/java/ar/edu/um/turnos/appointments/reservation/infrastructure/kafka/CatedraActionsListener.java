package ar.edu.um.turnos.appointments.reservation.infrastructure.kafka;

import ar.edu.um.turnos.appointments.reservation.application.CatedraEvent;
import ar.edu.um.turnos.appointments.reservation.application.CatedraEventService;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.EventResult;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escucha el topic de acciones (alumnos.turnos.acciones.{groupId}): pedidos de teléfono y
 * resultados de las reservas.
 *
 * - Un mensaje que no se puede leer, o de un tipo que no conocemos, se registra y se saltea:
 *   reintentarlo no lo va a arreglar.
 * - Si falla la base al aplicarlo, la excepción sube y Spring Kafka lo reintenta (ver
 *   KafkaErrorConfiguration). Como cada evento se aplica una sola vez, reintentar no duplica nada.
 */
@Component
public class CatedraActionsListener {

    private static final Logger LOG = LoggerFactory.getLogger(CatedraActionsListener.class);

    private static final Map<String, CatedraEvent.Type> TYPES = Map.of(
        "AdditionalInformationRequested",
        CatedraEvent.Type.PHONE_REQUESTED,
        "AdditionalInformationRejected",
        CatedraEvent.Type.PHONE_REJECTED,
        "AppointmentConfirmed",
        CatedraEvent.Type.CONFIRMED,
        "AppointmentProcessExpired",
        CatedraEvent.Type.EXPIRED,
        "AppointmentProcessInvalid",
        CatedraEvent.Type.INVALID,
        "AppointmentCancelled",
        CatedraEvent.Type.CANCELLED
    );

    private final CatedraEventService events;
    // La referencia pide ignorar los campos nuevos que la cátedra pueda agregar
    private final JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    public CatedraActionsListener(CatedraEventService events) {
        this.events = events;
    }

    @KafkaListener(
        topics = "${catedra.kafka.actions-topic:alumnos.turnos.acciones.local}",
        groupId = "${catedra.kafka.group-id:turnos-local}",
        autoStartup = "${catedra.kafka.enabled:true}",
        // Con un consumer group nuevo se lee el topic desde el principio, para no perder eventos
        // publicados antes del primer arranque. Los que no son nuestros se ignoran.
        properties = "auto.offset.reset=earliest"
    )
    public void onAction(String message) {
        ActionMessage action;
        try {
            action = json.readValue(message, ActionMessage.class);
        } catch (JacksonException e) {
            LOG.warn("Se ignora un mensaje de acciones que no es JSON válido: {}", e.getOriginalMessage());
            return;
        }

        CatedraEvent.Type type = action.eventType() == null ? null : TYPES.get(action.eventType());
        if (type == null || action.eventId() == null || action.reservationProcessId() == null) {
            LOG.warn("Se ignora el evento {} de tipo {}: falta un dato o el tipo no se conoce", action.eventId(), action.eventType());
            return;
        }

        CatedraEvent event = new CatedraEvent(
            action.eventId(),
            type,
            action.reservationProcessId(),
            action.reservationId(),
            action.expiresAt(),
            action.reason(),
            action.message()
        );
        EventResult result = events.handle(event);
        switch (result) {
            case APPLIED -> LOG.info(
                "Evento {} ({}) aplicado al proceso {}",
                event.eventId(),
                action.eventType(),
                event.reservationProcessId()
            );
            case DUPLICATE -> LOG.info("El evento {} ya se había aplicado, se ignora", event.eventId());
            case UNKNOWN_PROCESS -> LOG.warn(
                "El evento {} es de un proceso que no existe acá: {}",
                event.eventId(),
                event.reservationProcessId()
            );
        }
    }

    /**
     * Los campos de todos los eventos de acciones (secciones 15.4 a 15.10 de la referencia).
     */
    record ActionMessage(
        String eventId,
        String eventType,
        String reservationProcessId,
        Long reservationId,
        Instant expiresAt,
        String reason,
        String message
    ) {}
}
