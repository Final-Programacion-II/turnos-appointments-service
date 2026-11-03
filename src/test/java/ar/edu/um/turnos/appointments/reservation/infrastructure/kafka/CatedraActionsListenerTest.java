package ar.edu.um.turnos.appointments.reservation.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.um.turnos.appointments.reservation.application.CatedraEvent;
import ar.edu.um.turnos.appointments.reservation.application.CatedraEventService;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.EventResult;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Traducción de los mensajes de Kafka, con los ejemplos de la sección 15 de la referencia.
 */
class CatedraActionsListenerTest {

    private CatedraEventService events;
    private CatedraActionsListener listener;

    @BeforeEach
    void preparar() {
        events = mock(CatedraEventService.class);
        when(events.handle(any())).thenReturn(EventResult.APPLIED);
        listener = new CatedraActionsListener(events);
    }

    @Test
    void pedidoDeTelefono() {
        listener.onAction(
            """
            { "eventId": "request-event-8a31", "eventType": "AdditionalInformationRequested",
              "reservationProcessId": "process-a92b47d0", "holdId": "hold-6c0c2f41",
              "requiredField": "PHONE_NUMBER", "message": "Ingrese un numero de telefono para continuar.",
              "expiresAt": "2026-09-10T14:44:50Z", "occurredAt": "2026-09-10T14:35:10Z", "schemaVersion": 1 }
            """
        );

        CatedraEvent evento = recibido();
        assertThat(evento.type()).isEqualTo(CatedraEvent.Type.PHONE_REQUESTED);
        assertThat(evento.eventId()).isEqualTo("request-event-8a31");
        assertThat(evento.reservationProcessId()).isEqualTo("process-a92b47d0");
        assertThat(evento.expiresAt()).isEqualTo(Instant.parse("2026-09-10T14:44:50Z"));
    }

    @Test
    void confirmacion() {
        listener.onAction(
            """
            { "eventId": "confirmation-event-7d10", "eventType": "AppointmentConfirmed", "groupId": "proyecto-juan-perez",
              "reservationProcessId": "process-a92b47d0", "holdId": "hold-6c0c2f41", "requestEventId": "request-event-8a31",
              "reservationId": 8251, "professionalId": 15, "date": "2026-09-14", "startTime": "09:30",
              "status": "CONFIRMED", "occurredAt": "2026-09-10T14:36:01Z", "schemaVersion": 1 }
            """
        );

        CatedraEvent evento = recibido();
        assertThat(evento.type()).isEqualTo(CatedraEvent.Type.CONFIRMED);
        assertThat(evento.reservationId()).isEqualTo(8251L);
    }

    @Test
    void rechazoDelTelefono() {
        listener.onAction(
            """
            { "eventId": "rejection-event-7d11", "eventType": "AdditionalInformationRejected",
              "reservationProcessId": "process-a92b47d0", "reason": "INVALID_PHONE_NUMBER",
              "message": "El telefono ingresado no es valido.", "expiresAt": "2026-09-10T14:44:50Z", "schemaVersion": 1 }
            """
        );

        CatedraEvent evento = recibido();
        assertThat(evento.type()).isEqualTo(CatedraEvent.Type.PHONE_REJECTED);
        assertThat(evento.message()).isEqualTo("El telefono ingresado no es valido.");
    }

    @Test
    void vencimientoInvalidoYCancelacion() {
        listener.onAction(
            "{\"eventId\":\"e1\",\"eventType\":\"AppointmentProcessExpired\",\"reservationProcessId\":\"p\",\"reason\":\"HOLD_EXPIRED\"}"
        );
        assertThat(recibido().type()).isEqualTo(CatedraEvent.Type.EXPIRED);

        listener.onAction(
            "{\"eventId\":\"e2\",\"eventType\":\"AppointmentProcessInvalid\",\"reservationProcessId\":\"p\",\"reason\":\"X\"}"
        );
        assertThat(recibido().type()).isEqualTo(CatedraEvent.Type.INVALID);

        listener.onAction("{\"eventId\":\"e3\",\"eventType\":\"AppointmentCancelled\",\"reservationProcessId\":\"p\",\"reason\":null}");
        assertThat(recibido().type()).isEqualTo(CatedraEvent.Type.CANCELLED);
    }

    @Test
    void ignoraLoQueNoPuedeLeer() {
        listener.onAction("esto no es json");
        listener.onAction("{\"eventId\":\"e1\",\"eventType\":\"OtroEvento\",\"reservationProcessId\":\"p\"}");
        listener.onAction("{\"eventType\":\"AppointmentConfirmed\",\"reservationProcessId\":\"p\"}");

        verify(events, never()).handle(any());
    }

    private CatedraEvent recibido() {
        ArgumentCaptor<CatedraEvent> captor = ArgumentCaptor.forClass(CatedraEvent.class);
        verify(events, atLeastOnce()).handle(captor.capture());
        return captor.getValue();
    }
}
