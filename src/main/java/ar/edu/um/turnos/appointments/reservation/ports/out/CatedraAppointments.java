package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraAppointment;
import java.util.List;

/**
 * Reservas de la cuenta técnica en la cátedra. Si la cátedra rechaza la operación, se lanza
 * CatedraRejectedException; si no responde, CatedraUnavailableException.
 */
public interface CatedraAppointments {
    /**
     * GET /api/appointments?externalPatientId=...: todas las reservas de un usuario.
     */
    List<CatedraAppointment> appointmentsOf(String externalPatientId);

    /**
     * POST /api/appointments/{reservationProcessId}/cancel. En la cátedra es idempotente: cancelar
     * una reserva ya cancelada devuelve su estado actual, así que se puede reintentar.
     */
    void cancel(String reservationProcessId, String reason);
}
