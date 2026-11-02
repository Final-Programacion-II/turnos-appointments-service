package ar.edu.um.turnos.appointments.reservation.domain;

/**
 * Paciente del turno, como se informa a la cátedra.
 *
 * @param externalPatientId identifica al usuario ante la cátedra (ver {@link #externalIdOf(long)}).
 */
public record Patient(String externalPatientId, String firstName, String lastName) {
    /**
     * La cátedra no conoce a nuestros usuarios: les mandamos un id estable armado con el id
     * interno, que no cambia aunque el usuario cambie su login o su email.
     */
    public static String externalIdOf(long userId) {
        return "user-" + userId;
    }
}
