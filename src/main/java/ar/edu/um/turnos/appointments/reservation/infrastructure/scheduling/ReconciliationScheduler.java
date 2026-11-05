package ar.edu.um.turnos.appointments.reservation.infrastructure.scheduling;

import ar.edu.um.turnos.appointments.reservation.application.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre la reconciliación poco después de arrancar (así se recuperan los procesos que quedaron
 * abiertos con el servicio apagado) y después cada cierto intervalo.
 */
@Component
@ConditionalOnProperty(name = "reservation.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class ReconciliationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationScheduler.class);

    private final ReconciliationService reconciliation;

    public ReconciliationScheduler(ReconciliationService reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(
        initialDelayString = "${reservation.reconciliation.initial-delay:20s}",
        fixedDelayString = "${reservation.reconciliation.interval:60s}"
    )
    public void reconcile() {
        try {
            ReconciliationService.Result result = reconciliation.reconcile();
            if (result.checked() > 0) {
                LOG.info(
                    "Reconciliación: {} procesos revisados, {} actualizados, {} sin revisar (cátedra no disponible)",
                    result.checked(),
                    result.changed(),
                    result.skipped()
                );
            }
        } catch (RuntimeException e) {
            // Un error no tiene que frenar las próximas pasadas
            LOG.warn("Falló la reconciliación de reservas: {}", e.getMessage());
        }
    }
}
