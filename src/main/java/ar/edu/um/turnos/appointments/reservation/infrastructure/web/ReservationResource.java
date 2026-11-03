package ar.edu.um.turnos.appointments.reservation.infrastructure.web;

import ar.edu.um.turnos.appointments.reservation.application.ReservationService;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reservas del usuario autenticado. El dueño sale siempre del JWT, nunca del body: así nadie
 * puede reservar ni consultar a nombre de otro.
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationResource {

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
