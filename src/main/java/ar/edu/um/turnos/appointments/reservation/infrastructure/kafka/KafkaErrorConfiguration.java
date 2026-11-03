package ar.edu.um.turnos.appointments.reservation.infrastructure.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorConfiguration {

    /**
     * Si aplicar un evento falla (por ejemplo, la base no responde), se reintenta 5 veces con 2
     * segundos entre intentos. Si sigue fallando, se registra en el log y se pasa al siguiente,
     * para no trabar el topic. El reintento queda acotado; lo que quede pendiente se recupera
     * consultando a la cátedra por REST.
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(2000L, 5));
    }
}
