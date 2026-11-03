package ar.edu.um.turnos.appointments.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.edu.um.turnos.appointments.reservation.domain.CatedraRejectedException;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.InvalidPhoneNumberException;
import ar.edu.um.turnos.appointments.reservation.domain.InvalidReservationStateException;
import ar.edu.um.turnos.appointments.reservation.domain.Occupancy;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.domain.ProfessionalAgenda;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationNotFoundException;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.domain.SlotNotAvailableException;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import ar.edu.um.turnos.appointments.reservation.ports.out.PhoneSubmissionPublisher;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
    private InMemoryReservationProcesses procesos;
    private List<Occupancy> ocupaciones;
    private List<PhoneSubmissionPublisher.PhoneSubmission> telefonos;
    private boolean kafkaCaido;
    private ReservationService service;

    @BeforeEach
    void preparar() {
        catedra = new CatedraFalsa();
        procesos = new InMemoryReservationProcesses();
        telefonos = new ArrayList<>();
        ocupaciones = new ArrayList<>();
        AvailabilityService availability = new AvailabilityService(id -> agenda(), (externalId, from, to) -> ocupaciones, RELOJ);
        PhoneSubmissionPublisher publisher = envio -> {
            if (kafkaCaido) {
                throw new CatedraUnavailableException("Kafka no responde", null);
            }
            telefonos.add(envio);
        };
        service = new ReservationService(availability, catedra, procesos, publisher, RELOJ);
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
        assertThat(procesos.find(proceso.id()).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
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
        assertThat(procesos.saved).isEmpty();
    }

    @Test
    void siLaConfirmacionNoLlegaQuedaRetenidoYSePuedeReintentar() {
        catedra.confirmacionCaida = true;

        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));
        assertThat(proceso.status()).isEqualTo(ReservationStatus.HELD);
        assertThat(procesos.find(proceso.id()).status()).isEqualTo(ReservationStatus.HELD);

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

    @Test
    void mandaElTelefonoConElPedidoQueResponde() {
        ReservationProcess proceso = esperandoTelefono();

        ReservationProcess enviado = service.submitPhone(USUARIO, proceso.id(), "+54 9 (261) 555-1234");

        assertThat(enviado.status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
        assertThat(telefonos).hasSize(1);
        PhoneSubmissionPublisher.PhoneSubmission envio = telefonos.get(0);
        assertThat(envio.phoneNumber()).isEqualTo("+5492615551234");
        assertThat(envio.requestEventId()).isEqualTo("request-1");
        assertThat(envio.reservationProcessId()).isEqualTo("process-1");
        assertThat(envio.holdId()).isEqualTo("hold-1");
        assertThat(envio.eventId()).isNotNull();
        assertThat(procesos.find(proceso.id()).phoneNumber()).isEqualTo("+5492615551234");
    }

    @Test
    void unTelefonoInvalidoNoSeManda() {
        ReservationProcess proceso = esperandoTelefono();

        assertThatThrownBy(() -> service.submitPhone(USUARIO, proceso.id(), "123")).isInstanceOf(InvalidPhoneNumberException.class);
        assertThat(telefonos).isEmpty();
    }

    @Test
    void sinPedidoDeLaCatedraNoSeMandaElTelefono() {
        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));

        assertThatThrownBy(() -> service.submitPhone(USUARIO, proceso.id(), "+5492615551234")).isInstanceOf(
            InvalidReservationStateException.class
        );
        assertThat(telefonos).isEmpty();
    }

    @Test
    void siKafkaNoRespondeSigueEsperandoElTelefono() {
        ReservationProcess proceso = esperandoTelefono();
        kafkaCaido = true;

        assertThatThrownBy(() -> service.submitPhone(USUARIO, proceso.id(), "+5492615551234")).isInstanceOf(
            CatedraUnavailableException.class
        );
        assertThat(procesos.find(proceso.id()).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);

        kafkaCaido = false;
        assertThat(service.submitPhone(USUARIO, proceso.id(), "+5492615551234").status()).isEqualTo(ReservationStatus.PHONE_SUBMITTED);
    }

    @Test
    void elTelefonoDeUnaReservaAjenaNoSeManda() {
        ReservationProcess proceso = esperandoTelefono();

        assertThatThrownBy(() -> service.submitPhone(8, proceso.id(), "+5492615551234")).isInstanceOf(ReservationNotFoundException.class);
        assertThat(telefonos).isEmpty();
    }

    /**
     * Una reserva a la que la cátedra ya le pidió el teléfono (como si hubiera llegado el evento).
     */
    private ReservationProcess esperandoTelefono() {
        ReservationProcess proceso = service.start(USUARIO, pedido(LocalTime.of(9, 30)));
        procesos.update(proceso.id(), p -> p.phoneRequested("request-1", VENCE, RELOJ.instant()));
        return proceso;
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
}
