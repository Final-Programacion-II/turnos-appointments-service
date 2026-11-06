package ar.edu.um.turnos.appointments.reservation.infrastructure;

import ar.edu.um.turnos.appointments.reservation.application.AvailabilityService;
import ar.edu.um.turnos.appointments.reservation.application.CatedraEventService;
import ar.edu.um.turnos.appointments.reservation.application.ReconciliationService;
import ar.edu.um.turnos.appointments.reservation.application.ReservationService;
import ar.edu.um.turnos.appointments.reservation.infrastructure.catedra.CatedraProperties;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraAppointments;
import ar.edu.um.turnos.appointments.reservation.ports.out.CatedraReservations;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import ar.edu.um.turnos.appointments.reservation.ports.out.PhoneSubmissionPublisher;
import ar.edu.um.turnos.appointments.reservation.ports.out.ProfessionalAgendaSource;
import ar.edu.um.turnos.appointments.reservation.ports.out.ReservationProcessRepository;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableConfigurationProperties(CatedraProperties.class)
// JHipster solo busca repositorios de Spring Data en su paquete "repository": el de reservas se agrega acá
@EnableJpaRepositories(basePackages = "ar.edu.um.turnos.appointments.reservation.infrastructure.persistence")
public class ReservationConfiguration {

    /**
     * Los turnos son horas de reloj de los consultorios, así que "hoy" y "ahora" se calculan en
     * esa zona y no en la del servidor (en Docker, la del contenedor suele ser UTC).
     */
    @Bean
    public Clock reservationClock(@Value("${reservation.zone:America/Argentina/Mendoza}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Bean
    public AvailabilityService availabilityService(ProfessionalAgendaSource agendas, OccupancySource occupancies, Clock reservationClock) {
        return new AvailabilityService(agendas, occupancies, reservationClock);
    }

    @Bean
    public ReservationService reservationService(
        AvailabilityService availabilityService,
        CatedraReservations catedra,
        ReservationProcessRepository processes,
        PhoneSubmissionPublisher phonePublisher,
        CatedraAppointments catedraAppointments,
        Clock reservationClock
    ) {
        return new ReservationService(availabilityService, catedra, processes, phonePublisher, catedraAppointments, reservationClock);
    }

    @Bean
    public CatedraEventService catedraEventService(ReservationProcessRepository processes, Clock reservationClock) {
        return new CatedraEventService(processes, reservationClock);
    }

    @Bean
    public ReconciliationService reconciliationService(
        ReservationProcessRepository processes,
        CatedraAppointments catedraAppointments,
        Clock reservationClock
    ) {
        return new ReconciliationService(processes, catedraAppointments, reservationClock);
    }
}
