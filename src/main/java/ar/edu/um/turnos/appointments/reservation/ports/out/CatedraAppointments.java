package ar.edu.um.turnos.appointments.reservation.ports.out;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraAppointment;
import java.util.List;

/**
 * Reservas de la cuenta técnica en la cátedra. Si la cátedra no responde, se lanza
 * CatedraUnavailableException.
 */
public interface CatedraAppointments {
    /**
     * GET /api/appointments?externalPatientId=...: todas las reservas de un usuario.
     */
    List<CatedraAppointment> appointmentsOf(String externalPatientId);
}
