package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.Instant;

/**
 * Un proceso de reserva de un usuario, desde el hold hasta su resultado.
 *
 * Las respuestas de la cátedra llegan por REST y por Kafka, en cualquier orden y a veces
 * repetidas. Por eso cada método que aplica una respuesta de la cátedra:
 * - cambia el estado solo si la transición tiene sentido desde el estado actual;
 * - si no la tiene (respuesta repetida o tardía), no hace nada y devuelve false;
 * - nunca saca al proceso de un resultado: un aviso viejo no puede reabrirlo.
 *
 * Las acciones del usuario, en cambio, fallan con {@link InvalidReservationStateException}
 * si no corresponden, para que el usuario se entere.
 */
public class ReservationProcess {

    private static final int MAX_REASON_LENGTH = 500;

    private final Long id;
    private final long ownerId;
    private final Appointment appointment;
    private final Patient patient;
    private final String holdId;
    private final String reservationProcessId;
    private final Instant createdAt;

    private ReservationStatus status;
    private Instant expiresAt;
    private String requestEventId;
    private String phoneNumber;
    private Long reservationId;
    private String statusReason;
    private Instant updatedAt;

    /**
     * Rearma un proceso guardado. Para uno nuevo, usar {@link #held}.
     */
    public ReservationProcess(
        Long id,
        long ownerId,
        Appointment appointment,
        Patient patient,
        String holdId,
        String reservationProcessId,
        ReservationStatus status,
        Instant expiresAt,
        String requestEventId,
        String phoneNumber,
        Long reservationId,
        String statusReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.appointment = appointment;
        this.patient = patient;
        this.holdId = holdId;
        this.reservationProcessId = reservationProcessId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.requestEventId = requestEventId;
        this.phoneNumber = phoneNumber;
        this.reservationId = reservationId;
        this.statusReason = statusReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Proceso nuevo: la cátedra ya creó el hold y nos dio sus ids.
     */
    public static ReservationProcess held(
        long ownerId,
        Appointment appointment,
        Patient patient,
        String holdId,
        String reservationProcessId,
        Instant expiresAt,
        Instant now
    ) {
        return new ReservationProcess(
            null,
            ownerId,
            appointment,
            patient,
            holdId,
            reservationProcessId,
            ReservationStatus.HELD,
            expiresAt,
            null,
            null,
            null,
            null,
            now,
            now
        );
    }

    // --- Respuestas de la cátedra (REST o Kafka) ---

    /**
     * La cátedra aceptó la confirmación inicial (202), o avisó que ya la había aceptado.
     */
    public boolean initialConfirmationAccepted(Instant newExpiresAt, Instant now) {
        if (status != ReservationStatus.HELD) {
            // Ya avanzó: por ejemplo, el pedido de teléfono llegó por Kafka antes que la respuesta REST
            return false;
        }
        status = ReservationStatus.WAITING_FOR_PHONE;
        updateExpiration(newExpiresAt);
        changed(now, null);
        return true;
    }

    /**
     * AdditionalInformationRequested: la cátedra pide el teléfono. Se guarda el eventId porque
     * hay que mandarlo de vuelta junto con el teléfono.
     */
    public boolean phoneRequested(String requestEventId, Instant newExpiresAt, Instant now) {
        if (status != ReservationStatus.HELD && status != ReservationStatus.WAITING_FOR_PHONE) {
            return false;
        }
        status = ReservationStatus.WAITING_FOR_PHONE;
        this.requestEventId = requestEventId;
        updateExpiration(newExpiresAt);
        changed(now, null);
        return true;
    }

    /**
     * AdditionalInformationRejected: el teléfono no sirvió. Se puede mandar otro hasta que venza.
     */
    public boolean phoneRejected(String message, Instant newExpiresAt, Instant now) {
        if (status != ReservationStatus.PHONE_SUBMITTED) {
            return false;
        }
        status = ReservationStatus.WAITING_FOR_PHONE;
        updateExpiration(newExpiresAt);
        changed(now, message);
        return true;
    }

    /**
     * AppointmentConfirmed: el turno quedó reservado.
     */
    public boolean confirmed(long reservationId, Instant now) {
        if (!status.isOpen()) {
            return false;
        }
        status = ReservationStatus.CONFIRMED;
        this.reservationId = reservationId;
        changed(now, null);
        return true;
    }

    /**
     * HOLD_EXPIRED por REST o AppointmentProcessExpired por Kafka.
     */
    public boolean expired(String reason, Instant now) {
        return close(ReservationStatus.EXPIRED, reason, now);
    }

    /**
     * AppointmentProcessInvalid.
     */
    public boolean invalid(String reason, Instant now) {
        return close(ReservationStatus.INVALID, reason, now);
    }

    /**
     * La cátedra rechazó la confirmación inicial con un error que no se arregla reintentando.
     */
    public boolean failed(String reason, Instant now) {
        return close(ReservationStatus.FAILED, reason, now);
    }

    /**
     * AppointmentCancelled. Solo se cancela una reserva confirmada; si ya estaba cancelada, no cambia.
     */
    public boolean cancelled(String reason, Instant now) {
        if (status != ReservationStatus.CONFIRMED) {
            return false;
        }
        status = ReservationStatus.CANCELLED;
        changed(now, reason);
        return true;
    }

    // --- Acciones del usuario ---

    /**
     * El usuario informó su teléfono. Solo tiene sentido si la cátedra ya lo pidió.
     */
    public void phoneSubmitted(String phoneNumber, Instant now) {
        if (status != ReservationStatus.WAITING_FOR_PHONE || requestEventId == null) {
            throw new InvalidReservationStateException("No se puede enviar el teléfono en el estado " + status);
        }
        status = ReservationStatus.PHONE_SUBMITTED;
        this.phoneNumber = phoneNumber;
        changed(now, null);
    }

    public boolean belongsTo(long userId) {
        return ownerId == userId;
    }

    private boolean close(ReservationStatus result, String reason, Instant now) {
        if (!status.isOpen()) {
            return false;
        }
        status = result;
        changed(now, reason);
        return true;
    }

    private void updateExpiration(Instant newExpiresAt) {
        if (newExpiresAt != null) {
            expiresAt = newExpiresAt;
        }
    }

    private void changed(Instant now, String reason) {
        updatedAt = now;
        statusReason = reason == null || reason.length() <= MAX_REASON_LENGTH ? reason : reason.substring(0, MAX_REASON_LENGTH);
    }

    public Long id() {
        return id;
    }

    public long ownerId() {
        return ownerId;
    }

    public Appointment appointment() {
        return appointment;
    }

    public Patient patient() {
        return patient;
    }

    public String holdId() {
        return holdId;
    }

    public String reservationProcessId() {
        return reservationProcessId;
    }

    public ReservationStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public String requestEventId() {
        return requestEventId;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public Long reservationId() {
        return reservationId;
    }

    public String statusReason() {
        return statusReason;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
