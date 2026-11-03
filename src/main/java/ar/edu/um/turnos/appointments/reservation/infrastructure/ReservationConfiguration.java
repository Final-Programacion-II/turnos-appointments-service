package ar.edu.um.turnos.appointments.reservation.infrastructure;

import ar.edu.um.turnos.appointments.reservation.application.AvailabilityService;
import ar.edu.um.turnos.appointments.reservation.ports.out.OccupancySource;
import ar.edu.um.turnos.appointments.reservation.ports.out.ProfessionalAgendaSource;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
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
}
