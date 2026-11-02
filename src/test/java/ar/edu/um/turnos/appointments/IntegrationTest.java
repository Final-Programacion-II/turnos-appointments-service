package ar.edu.um.turnos.appointments;

import ar.edu.um.turnos.appointments.config.AsyncSyncConfiguration;
import ar.edu.um.turnos.appointments.config.DatabaseTestcontainer;
import ar.edu.um.turnos.appointments.config.JacksonConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Base composite annotation for integration tests.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    classes = {
        AppointmentsApp.class,
        JacksonConfiguration.class,
        AsyncSyncConfiguration.class,
        ar.edu.um.turnos.appointments.config.JacksonHibernateConfiguration.class,
    }
)
@ImportTestcontainers(DatabaseTestcontainer.class)
public @interface IntegrationTest {}
