package ar.edu.um.turnos.appointments.reservation.ports.in;

/**
 * Cerrar por REST los procesos que vencieron sin recibir su resultado por Kafka.
 */
public interface ReconcileReservationsUseCase {
    /**
     * Una pasada de reconciliación.
     */
    Result reconcile();

    /**
     * @param checked procesos revisados en esta pasada.
     * @param changed procesos que cambiaron de estado.
     * @param skipped procesos que no se pudieron revisar porque la cátedra no respondió.
     */
    record Result(int checked, int changed, int skipped) {}
}
