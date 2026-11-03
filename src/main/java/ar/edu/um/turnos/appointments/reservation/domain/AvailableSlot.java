package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.LocalTime;

public record AvailableSlot(LocalTime startTime, LocalTime endTime) {}
