package ar.edu.um.turnos.appointments.reservation.ports.in;

import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.EventResult;

/**
 * Aplicar a un proceso de reserva un evento que publicó la cátedra.
 */
public interface HandleCatedraEventUseCase {
    /**
     * @return si el evento se aplicó, si ya se había procesado o si no hay proceso para él.
     */
    EventResult handle(CatedraEvent event);
}
