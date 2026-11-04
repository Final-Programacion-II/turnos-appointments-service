package ar.edu.um.turnos.appointments.reservation.application;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraAppointment;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraAppointments;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cierra los procesos que vencieron sin recibir su resultado por Kafka (sección 18.4 de la
 * referencia: reconciliar por REST con los ids guardados).
 *
 * Se revisan solo los procesos abiertos cuyo vencimiento pasó hace más de un margen: lo normal
 * es que el resultado llegue por Kafka en segundos, y no tiene sentido adelantarse. Se hace una
 * consulta a la cátedra por usuario, no por proceso.
 */
public class ReconciliationService {

    /** Tiempo que se le da a Kafka después del vencimiento antes de preguntar por REST. */
    static final Duration GRACE = Duration.ofMinutes(1);
    /** Tope por pasada, para que una pasada nunca sea demasiado larga. */
    static final int MAX_PER_RUN = 50;

    private final ReservationProcessRepository processes;
    private final CatedraAppointments catedra;
    private final Clock clock;

    public ReconciliationService(ReservationProcessRepository processes, CatedraAppointments catedra, Clock clock) {
        this.processes = processes;
        this.catedra = catedra;
        this.clock = clock;
    }

    /**
     * Una pasada de reconciliación.
     */
    public Result reconcile() {
        Instant now = clock.instant();
        List<ReservationProcess> pending = processes.findOpenExpiredBefore(now.minus(GRACE), MAX_PER_RUN);

        Map<String, List<ReservationProcess>> byPatient = pending
            .stream()
            .collect(Collectors.groupingBy(p -> p.patient().externalPatientId(), LinkedHashMap::new, Collectors.toList()));

        int changed = 0;
        int skipped = 0;
        for (Map.Entry<String, List<ReservationProcess>> entry : byPatient.entrySet()) {
            Map<String, CatedraAppointment> remote;
            try {
                remote = catedra
                    .appointmentsOf(entry.getKey())
                    .stream()
                    .collect(Collectors.toMap(CatedraAppointment::reservationProcessId, Function.identity(), (a, b) -> a));
            } catch (CatedraUnavailableException e) {
                // Se reintenta en la próxima pasada; los procesos quedan como estaban
                skipped += entry.getValue().size();
                continue;
            }
            for (ReservationProcess process : entry.getValue()) {
                if (apply(process, remote.get(process.reservationProcessId()), now)) {
                    changed++;
                }
            }
        }
        return new Result(pending.size(), changed, skipped);
    }

    private boolean apply(ReservationProcess process, CatedraAppointment remote, Instant now) {
        ReservationStatus before = process.status();
        // update vuelve a leer el proceso con el registro bloqueado: si mientras tanto llegó el
        // evento por Kafka, se parte de ese estado y el dominio no lo pisa
        ReservationProcess after = processes.update(process.id(), p -> {
            if (remote == null) {
                p.holdExpiredWithoutReservation(now);
            } else {
                p.reconcileWith(remote, now);
            }
        });
        return after.status() != before;
    }

    /**
     * @param checked procesos revisados en esta pasada.
     * @param changed procesos que cambiaron de estado.
     * @param skipped procesos que no se pudieron revisar porque la cátedra no respondió.
     */
    public record Result(int checked, int changed, int skipped) {}
}
