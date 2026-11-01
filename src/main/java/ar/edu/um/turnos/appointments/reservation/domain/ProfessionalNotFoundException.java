package ar.edu.um.turnos.appointments.reservation.domain;

public class ProfessionalNotFoundException extends RuntimeException {

    public ProfessionalNotFoundException(long professionalId) {
        super("El profesional " + professionalId + " no existe en el catálogo");
    }
}
