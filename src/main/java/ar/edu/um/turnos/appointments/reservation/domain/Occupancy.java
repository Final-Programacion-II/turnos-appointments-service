package ar.edu.um.turnos.appointments.reservation.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Un turno que la cátedra informa como no disponible: confirmado (CONFIRMED) o retenido
 * por un hold (HELD) hasta expiresAt.
 */
public record Occupancy(LocalDate date, LocalTime startTime, Status status, Instant expiresAt) {
    public enum Status {
        CONFIRMED,
        HELD,
    }

    /**
     * Un hold vencido ya no ocupa el turno, aunque la cátedra todavía lo informe.
     */
    public boolean blocksAt(Instant now) {
        return status == Status.CONFIRMED || expiresAt == null || expiresAt.isAfter(now);
    }
}
