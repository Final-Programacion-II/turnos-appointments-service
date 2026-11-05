package ar.edu.um.turnos.appointments.reservation.application;

import ar.edu.um.turnos.appointments.reservation.domain.ReservationNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Repositorio en memoria para los tests de los servicios. Guarda copias, como una base de
 * verdad: lo que se modifica sin pasar por update o applyOnce no queda guardado.
 */
class InMemoryReservationProcesses implements ReservationProcessRepository {

    final Map<Long, ReservationProcess> saved = new HashMap<>();
    final Set<String> processedEvents = new HashSet<>();
    private long nextId = 1;

    @Override
    public ReservationProcess create(ReservationProcess p) {
        ReservationProcess withId = copy(p, nextId++);
        saved.put(withId.id(), withId);
        return copy(withId, withId.id());
    }

    @Override
    public Optional<ReservationProcess> findById(long id) {
        return Optional.ofNullable(saved.get(id)).map(p -> copy(p, id));
    }

    @Override
    public ReservationPage findByOwner(long ownerId, Set<ReservationStatus> statuses, int page, int size) {
        List<ReservationProcess> all = saved
            .values()
            .stream()
            .filter(p -> p.ownerId() == ownerId && (statuses.isEmpty() || statuses.contains(p.status())))
            .sorted(
                Comparator.comparing((ReservationProcess p) -> p.appointment().date())
                    .thenComparing(p -> p.appointment().startTime())
                    .reversed()
            )
            .map(p -> copy(p, p.id()))
            .toList();
        List<ReservationProcess> pageItems = all
            .stream()
            .skip((long) page * size)
            .limit(size)
            .toList();
        return new ReservationPage(pageItems, all.size());
    }

    @Override
    public List<ReservationProcess> findOpenExpiredBefore(Instant limit, int max) {
        return saved
            .values()
            .stream()
            .filter(p -> p.status().isOpen() && p.expiresAt() != null && p.expiresAt().isBefore(limit))
            .sorted(Comparator.comparing(ReservationProcess::expiresAt))
            .limit(max)
            .map(p -> copy(p, p.id()))
            .toList();
    }

    @Override
    public ReservationProcess update(long id, Consumer<ReservationProcess> change) {
        ReservationProcess p = findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
        change.accept(p);
        saved.put(id, copy(p, id));
        return p;
    }

    @Override
    public EventResult applyOnce(String reservationProcessId, String eventId, Consumer<ReservationProcess> change) {
        Optional<ReservationProcess> found = saved
            .values()
            .stream()
            .filter(p -> p.reservationProcessId().equals(reservationProcessId))
            .findFirst();
        if (found.isEmpty()) {
            return EventResult.UNKNOWN_PROCESS;
        }
        if (!processedEvents.add(eventId)) {
            return EventResult.DUPLICATE;
        }
        update(found.orElseThrow().id(), change);
        return EventResult.APPLIED;
    }

    ReservationProcess find(long id) {
        return saved.get(id);
    }

    private static ReservationProcess copy(ReservationProcess p, long id) {
        return new ReservationProcess(
            id,
            p.ownerId(),
            p.appointment(),
            p.patient(),
            p.holdId(),
            p.reservationProcessId(),
            p.status(),
            p.expiresAt(),
            p.requestEventId(),
            p.phoneNumber(),
            p.reservationId(),
            p.statusReason(),
            p.createdAt(),
            p.updatedAt()
        );
    }
}
