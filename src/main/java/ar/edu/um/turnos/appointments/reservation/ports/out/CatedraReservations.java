package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Operaciones de reserva en la API de la cátedra. Si la cátedra rechaza la operación, se lanza
 * CatedraRejectedException; si no se puede saber qué pasó (no responde, timeout, error 5xx),
 * CatedraUnavailableException.
 */
public interface CatedraReservations {
    /**
     * POST /api/appointment-holds: retiene el turno por unos minutos.
     */
    Hold createHold(long professionalExternalId, LocalDate date, LocalTime startTime, String externalPatientId);

    /**
     * POST /api/appointment-holds/{holdId}/confirm: confirmación inicial.
     *
     * @return el nuevo vencimiento del proceso.
     */
    Instant confirmHold(String holdId, Patient patient);

    record Hold(String holdId, String reservationProcessId, Instant expiresAt) {}
}
