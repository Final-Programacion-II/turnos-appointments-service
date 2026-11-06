package ar.edu.um.turnos.appointments.reservation.infrastructure.web;

import ar.edu.um.turnos.appointments.reservation.application.ReservationService;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository.ReservationPage;
import ar.edu.um.turnos.appointments.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tech.jhipster.web.util.PaginationUtil;

/**
 * Reservas del usuario autenticado. El dueño sale siempre del JWT, nunca del body: así nadie
 * puede reservar ni consultar a nombre de otro.
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationResource {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReservationService reservationService;

    public ReservationResource(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /**
     * {@code POST /api/reservations}: pide el hold y hace la confirmación inicial.
     * Responde 201 con el proceso. Lo normal es que quede WAITING_FOR_PHONE; si quedó HELD, la
     * cátedra no respondió a la confirmación y se puede reintentar con /confirm.
     */
    @PostMapping
    public ResponseEntity<ReservationDTO> start(@Valid @RequestBody StartReservationRequest request) {
        ReservationProcess process = reservationService.start(
            currentUserId(),
            new ReservationService.StartReservation(
                request.professionalId(),
                request.date(),
                request.startTime(),
                request.patientFirstName().trim(),
                request.patientLastName().trim()
            )
        );
        return ResponseEntity.created(URI.create("/api/reservations/" + process.id())).body(ReservationDTO.of(process));
    }

    /**
     * {@code GET /api/reservations?status=CONFIRMED&page=0&size=20}: las reservas del usuario,
     * del turno más lejano al más cercano. {@code status} se puede repetir; sin él, vienen todas.
     * El total va en el header X-Total-Count, como en el resto de los listados de JHipster.
     */
    @GetMapping
    public ResponseEntity<List<ReservationDTO>> list(
        @RequestParam(name = "status", required = false) List<ReservationStatus> status,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page tiene que ser 0 o más, y size entre 1 y " + MAX_PAGE_SIZE);
        }
        EnumSet<ReservationStatus> statuses = EnumSet.noneOf(ReservationStatus.class);
        if (status != null) {
            statuses.addAll(status);
        }
        ReservationPage result = reservationService.list(currentUserId(), statuses, page, size);
        List<ReservationDTO> items = result.items().stream().map(ReservationDTO::of).toList();
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(
            ServletUriComponentsBuilder.fromCurrentRequest(),
            new PageImpl<>(items, PageRequest.of(page, size), result.total())
        );
        return ResponseEntity.ok().headers(headers).body(items);
    }

    /**
     * {@code GET /api/reservations/{id}}: estado de una reserva propia. Una ajena da 404.
     */
    @GetMapping("/{id}")
    public ReservationDTO get(@PathVariable("id") long id) {
        return ReservationDTO.of(reservationService.get(currentUserId(), id));
    }

    /**
     * {@code POST /api/reservations/{id}/confirm}: reintenta la confirmación inicial si quedó HELD.
     */
    @PostMapping("/{id}/confirm")
    public ReservationDTO retryConfirmation(@PathVariable("id") long id) {
        return ReservationDTO.of(reservationService.retryConfirmation(currentUserId(), id));
    }

    /**
     * {@code POST /api/reservations/{id}/phone}: manda el teléfono que pidió la cátedra.
     * Responde con el proceso en PHONE_SUBMITTED; el resultado llega después por Kafka y se ve
     * consultando la reserva.
     */
    @PostMapping("/{id}/phone")
    public ReservationDTO submitPhone(@PathVariable("id") long id, @Valid @RequestBody PhoneRequest request) {
        return ReservationDTO.of(reservationService.submitPhone(currentUserId(), id, request.phoneNumber()));
    }

    /**
     * {@code POST /api/reservations/{id}/cancel}: cancela una reserva confirmada propia. El body
     * es opcional. Cancelar una ya cancelada devuelve la reserva como está.
     */
    @PostMapping("/{id}/cancel")
    public ReservationDTO cancel(@PathVariable("id") long id, @Valid @RequestBody(required = false) CancelRequest request) {
        String reason = request == null ? null : request.reason();
        return ReservationDTO.of(reservationService.cancel(currentUserId(), id, reason));
    }

    private static long currentUserId() {
        // Los tokens que emite este servicio traen el claim userId
        return SecurityUtils.getCurrentUserId().orElseThrow(() -> new AccessDeniedException("El token no identifica al usuario"));
    }

    /**
     * Mismos límites que la cátedra para los nombres (2 a 100 caracteres).
     */
    public record StartReservationRequest(
        @NotNull Long professionalId,
        @NotNull LocalDate date,
        @NotNull LocalTime startTime,
        @NotBlank @Size(min = 2, max = 100) String patientFirstName,
        @NotBlank @Size(min = 2, max = 100) String patientLastName
    ) {}

    /**
     * Mismo límite que la cátedra para el motivo.
     */
    public record CancelRequest(@Size(max = 500) String reason) {}

    /**
     * El formato lo valida el dominio, igual que la cátedra (acepta espacios, guiones y paréntesis).
     */
    public record PhoneRequest(@NotBlank @Size(max = 30) String phoneNumber) {}

    /**
     * Lo que ve el usuario. Los ids internos de la cátedra (holdId, reservationProcessId) no se
     * muestran: la app no los necesita.
     *
     * @param phoneRequested la cátedra ya pidió el teléfono: la app puede mostrar el campo para cargarlo.
     */
    public record ReservationDTO(
        long id,
        ReservationStatus status,
        long professionalId,
        String professionalName,
        String categoryName,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String patientFirstName,
        String patientLastName,
        Instant expiresAt,
        boolean phoneRequested,
        String statusReason
    ) {
        static ReservationDTO of(ReservationProcess p) {
            return new ReservationDTO(
                p.id(),
                p.status(),
                p.appointment().professionalId(),
                p.appointment().professionalName(),
                p.appointment().categoryName(),
                p.appointment().date(),
                p.appointment().startTime(),
                p.appointment().endTime(),
                p.patient().firstName(),
                p.patient().lastName(),
                p.expiresAt(),
                p.status() == ReservationStatus.WAITING_FOR_PHONE && p.requestEventId() != null,
                p.statusReason()
            );
        }
    }
}
