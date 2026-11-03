package ar.edu.um.turnos.appointments.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.domain.SlotNotAvailableException;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Prueba el servicio con dobles simples en lugar del catálogo, la cátedra y la base.
 */
class ReservationServiceTest {

    // Lunes 2/11/2026 a las 10:00 en Mendoza; el turno es el lunes siguiente
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-02T13:00:00Z"), ZoneId.of("America/Argentina/Mendoza"));
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    private static final Instant VENCE = Instant.parse("2026-11-02T13:15:00Z");
    private static final long USUARIO = 7;

    private CatedraFalsa catedra;
    private ProcesosEnMemoria procesos;
    private List<Occupancy> ocupaciones;
    private ReservationService service;

    @BeforeEach
    void preparar() {
        catedra = new CatedraFalsa();
        procesos = new ProcesosEnMemoria();
        ocupaciones = new ArrayList<>();
        AvailabilityService availability = new AvailabilityService(id -> agenda(), (externalId, from, to) -> ocupaciones, RELOJ);
        service = new ReservationService(availability, catedra, procesos, RELOJ);
    }

    @Test
    void reservaPideElHoldYLoConfirma() {
        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));

        assertThat(proceso.status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(proceso.id()).isNotNull();
        assertThat(proceso.ownerId()).isEqualTo(USUARIO);
        assertThat(proceso.holdId()).isEqualTo("hold-1");
        assertThat(proceso.appointment().endTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(proceso.appointment().professionalName()).isEqualTo("Ana Torres");

        // A la cátedra le llega el id externo del profesional y un id estable del usuario
        assertThat(catedra.holdsPedidos).containsExactly("15 2026-11-09 09:30 user-7");
        assertThat(catedra.confirmacionesPedidas).containsExactly("hold-1 user-7 Juan Perez");
        assertThat(procesos.buscar(proceso.id()).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
    }

    @Test
    void unTurnoOcupadoNoLlegaALaCatedra() {
        ocupaciones.add(new Occupancy(LUNES, LocalTime.of(9, 30), Occupancy.Status.CONFIRMED, null));

        assertThatThrownBy(() -> service.start(USUARIO, pedido(LocalTime.of(9, 30)))).isInstanceOf(SlotNotAvailableException.class);
        assertThat(catedra.holdsPedidos).isEmpty();
    }

    @Test
    void unHorarioFueraDeLaAgendaNoSeReserva() {
        assertThatThrownBy(() -> service.start(USUARIO, pedido(LocalTime.of(9, 15)))).isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void siLaCatedraRechazaElHoldNoSeGuardaNada() {
        catedra.rechazoDelHold = new CatedraRejectedException(409, "SLOT_ALREADY_HELD", "Slot already held");

        assertThatThrownBy(() -> service.start(USUARIO, pedido(LocalTime.of(9, 30))))
            .isInstanceOf(CatedraRejectedException.class)
            .hasMessageContaining("SLOT_ALREADY_HELD");
        assertThat(procesos.guardados).isEmpty();
    }

    @Test
    void siLaConfirmacionNoLlegaQuedaRetenidoYSePuedeReintentar() {
        catedra.confirmacionCaida = true;

        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));
        assertThat(proceso.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(procesos.buscar(proceso.id()).status()).isEqualTo(ReservationStatus.HELD);

        // Si sigue caída, el reintento informa el error
        assertThatThrownBy(() -> service.retryConfirmation(USUARIO, proceso.id())).isInstanceOf(CatedraUnavailableException.class);

        catedra.confirmacionCaida = false;
        assertThat(service.retryConfirmation(USUARIO, proceso.id()).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
    }

    @Test
    void siUnIntentoAnteriorLlegoElReintentoAvanzaIgual() {
        catedra.confirmacionCaida = true;
        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));

        // La primera confirmación sí había llegado: la cátedra avisa que ya estaba confirmado
        catedra.confirmacionCaida = false;
        catedra.rechazoDeLaConfirmacion = new CatedraRejectedException(409, "RESERVATION_PROCESS_ALREADY_CONFIRMED", null);

        assertThat(service.retryConfirmation(USUARIO, proceso.id()).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
    }

    @Test
    void unHoldVencidoCierraElProceso() {
        catedra.rechazoDeLaConfirmacion = new CatedraRejectedException(404, "HOLD_EXPIRED", "Hold expired");

        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));

        assertThat(proceso.status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(proceso.statusReason()).isEqualTo("HOLD_EXPIRED");

        // Ya terminó: reintentar no vuelve a llamar a la cátedra
        service.retryConfirmation(USUARIO, proceso.id());
        assertThat(catedra.confirmacionesPedidas).hasSize(1);
    }

    @Test
    void otroRechazoDejaElProcesoFallido() {
        catedra.rechazoDeLaConfirmacion = new CatedraRejectedException(404, "HOLD_NOT_FOUND", null);

        assertThat(service.start(USUARIO, pedido(LocalTime.of(9, 30))).status()).isEqualTo(ReservationStatus.FAILED);
    }

    @Test
    void cadaUsuarioVeSoloSusReservas() {
        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));

        assertThat(service.get(USUARIO, proceso.id()).id()).isEqualTo(proceso.id());
        assertThatThrownBy(() -> service.get(8, proceso.id())).isInstanceOf(ReservationNotFoundException.class);
        assertThatThrownBy(() -> service.retryConfirmation(8, proceso.id())).isInstanceOf(ReservationNotFoundException.class);
    }

    private static ReservationService.StartReservation pedido(LocalTime hora) {
        return new ReservationService.StartReservation(1051, LUNES, hora, "Juan", "Perez");
    }

    private static ProfessionalAgenda agenda() {
        return new ProfessionalAgenda(
            1051,
            15,
            "Ana",
            "Torres",
            "Cardiologia",
            true,
            1L,
            List.of(new ProfessionalAgenda.Schedule(21, DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(11, 0), 30))
        );
    }

    private static class CatedraFalsa implements CatedraReservations {

        final List<String> holdsPedidos = new ArrayList<>();
        final List<String> confirmacionesPedidas = new ArrayList<>();
        CatedraRejectedException rechazoDelHold;
        CatedraRejectedException rechazoDeLaConfirmacion;
        boolean confirmacionCaida;

        @Override
        public Hold createHold(long professionalExternalId, LocalDate date, LocalTime startTime, String externalPatientId) {
            holdsPedidos.add(professionalExternalId + " " + date + " " + startTime + " " + externalPatientId);
            if (rechazoDelHold != null) {
                throw rechazoDelHold;
            }
            return new Hold("hold-" + holdsPedidos.size(), "process-" + holdsPedidos.size(), VENCE);
        }

        @Override
        public Instant confirmHold(String holdId, Patient patient) {
            confirmacionesPedidas.add(holdId + " " + patient.externalPatientId() + " " + patient.firstName() + " " + patient.lastName());
            if (confirmacionCaida) {
                throw new CatedraUnavailableException("timeout", null);
            }
            if (rechazoDeLaConfirmacion != null) {
                throw rechazoDeLaConfirmacion;
            }
            return VENCE;
        }
    }

    /**
     * Guarda copias, como una base de verdad: lo que el servicio modifica sin llamar a update no
     * queda guardado.
     */
    private static class ProcesosEnMemoria implements ReservationProcessRepository {

        final Map<Long, ReservationProcess> guardados = new HashMap<>();
        private long proximoId = 1;

        @Override
        public ReservationProcess create(ReservationProcess p) {
            ReservationProcess conId = copia(p, proximoId++);
            guardados.put(conId.id(), conId);
            return copia(conId, conId.id());
        }

        @Override
        public Optional<ReservationProcess> findById(long id) {
            return Optional.ofNullable(guardados.get(id)).map(p -> copia(p, id));
        }

        @Override
        public ReservationProcess update(long id, Consumer<ReservationProcess> change) {
            ReservationProcess p = findById(id).orElseThrow(() -> new ReservationNotFoundException(id));
            change.accept(p);
            guardados.put(id, copia(p, id));
            return p;
        }

        ReservationProcess buscar(long id) {
            return guardados.get(id);
        }

        private static ReservationProcess copia(ReservationProcess p, long id) {
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
}
