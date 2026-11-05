package ar.edu.um.turnos.appointments.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.um.turnos.appointments.reservation.domain.Appointment;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraAppointment;
import ar.edu.um.turnos.appointments.reservation.domain.CatedraUnavailableException;
import ar.edu.um.turnos.appointments.reservation.domain.Patient;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationProcess;
import ar.edu.um.turnos.appointments.reservation.domain.ReservationStatus;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraAppointments;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T19:00:00Z");
    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private InMemoryReservationProcesses procesos;
    private CatedraFalsa catedra;
    private ReconciliationService service;

    @BeforeEach
    void preparar() {
        procesos = new InMemoryReservationProcesses();
        catedra = new CatedraFalsa();
        service = new ReconciliationService(procesos, catedra, RELOJ);
    }

    @Test
    void cierraLosProcesosVencidosSegunLaCatedra() {
        long vencido = esperandoTelefono("process-1", 7, AHORA.minusSeconds(600));
        long confirmado = esperandoTelefono("process-2", 7, AHORA.minusSeconds(600));
        catedra.responde(
            "user-7",
            remota("process-1", CatedraAppointment.Status.FAILED),
            remota("process-2", CatedraAppointment.Status.CONFIRMED)
        );

        ReconciliationService.Result resultado = service.reconcile();

        assertThat(resultado.checked()).isEqualTo(2);
        assertThat(resultado.changed()).isEqualTo(2);
        assertThat(procesos.find(vencido).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(procesos.find(confirmado).status()).isEqualTo(ReservationStatus.CONFIRMED);
        // Una sola consulta para los dos procesos del mismo usuario
        assertThat(catedra.consultas).containsExactly("user-7");
    }

    @Test
    void noSeAdelantaAKafka() {
        // Venció hace 30 segundos: todavía puede llegar el evento
        long reciente = esperandoTelefono("process-1", 7, AHORA.minusSeconds(30));
        // Todavía no venció
        long vigente = esperandoTelefono("process-2", 7, AHORA.plusSeconds(300));

        ReconciliationService.Result resultado = service.reconcile();

        assertThat(resultado.checked()).isEqualTo(0);
        assertThat(catedra.consultas).isEmpty();
        assertThat(procesos.find(reciente).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
        assertThat(procesos.find(vigente).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);
    }

    @Test
    void unHoldQueLaCatedraNoTieneVence() {
        long retenido = procesos.create(proceso("process-1", 7, AHORA.minusSeconds(600))).id();
        catedra.responde("user-7");

        service.reconcile();

        assertThat(procesos.find(retenido).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(procesos.find(retenido).statusReason()).isEqualTo("HOLD_EXPIRED");
    }

    @Test
    void siLaCatedraNoRespondeSeReintentaDespues() {
        long id = esperandoTelefono("process-1", 7, AHORA.minusSeconds(600));
        catedra.caida = true;

        ReconciliationService.Result resultado = service.reconcile();

        assertThat(resultado.skipped()).isEqualTo(1);
        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.WAITING_FOR_PHONE);

        catedra.caida = false;
        catedra.responde("user-7", remota("process-1", CatedraAppointment.Status.FAILED));
        service.reconcile();
        assertThat(procesos.find(id).status()).isEqualTo(ReservationStatus.EXPIRED);
    }

    @Test
    void cadaUsuarioSeConsultaAparte() {
        esperandoTelefono("process-1", 7, AHORA.minusSeconds(600));
        esperandoTelefono("process-2", 8, AHORA.minusSeconds(600));
        catedra.responde("user-7", remota("process-1", CatedraAppointment.Status.FAILED));
        catedra.responde("user-8", remota("process-2", CatedraAppointment.Status.FAILED));

        service.reconcile();

        assertThat(catedra.consultas).hasSize(2);
    }

    private long esperandoTelefono(String processId, long ownerId, Instant vence) {
        ReservationProcess p = procesos.create(proceso(processId, ownerId, vence));
        procesos.update(p.id(), proceso -> proceso.initialConfirmationAccepted(vence, AHORA.minusSeconds(900)));
        return p.id();
    }

    private static ReservationProcess proceso(String processId, long ownerId, Instant vence) {
        Appointment turno = new Appointment(
            1051,
            15,
            "Ana Torres",
            "Cardiologia",
            LocalDate.of(2026, 11, 10),
            LocalTime.of(9, 0),
            LocalTime.of(9, 30)
        );
        Patient paciente = new Patient(Patient.externalIdOf(ownerId), "Juan", "Perez");
        return ReservationProcess.held(ownerId, turno, paciente, "hold-" + processId, processId, vence, AHORA.minusSeconds(900));
    }

    private static CatedraAppointment remota(String processId, CatedraAppointment.Status estado) {
        return new CatedraAppointment(processId, estado, 8251L, null);
    }

    private static class CatedraFalsa implements CatedraAppointments {

        final Map<String, List<CatedraAppointment>> respuestas = new HashMap<>();
        final List<String> consultas = new ArrayList<>();
        boolean caida;

        void responde(String paciente, CatedraAppointment... reservas) {
            respuestas.put(paciente, List.of(reservas));
        }

        @Override
        public List<CatedraAppointment> appointmentsOf(String externalPatientId) {
            consultas.add(externalPatientId);
            if (caida) {
                throw new CatedraUnavailableException("timeout", null);
            }
            return respuestas.getOrDefault(externalPatientId, List.of());
        }

        @Override
        public void cancel(String reservationProcessId, String reason) {
            throw new UnsupportedOperationException("La reconciliación no cancela reservas");
        }
    }
}
