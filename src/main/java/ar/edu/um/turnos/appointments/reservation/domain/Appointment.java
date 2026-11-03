package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * El turno que se reserva. Los datos del profesional son una foto del momento de la reserva
 * (sirven para mostrarla), no el catálogo vigente.
 *
 * @param professionalId         id local del catálogo.
 * @param professionalExternalId id que conoce la cátedra.
 */
public record Appointment(
    long professionalId,
    long professionalExternalId,
    String professionalName,
    String categoryName,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime
) {}
