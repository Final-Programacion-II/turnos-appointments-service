package ar.edu.um.turnos.appointments.reservation;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Reglas del módulo de reservas, que es hexagonal (como la sincronización en el catálogo).
 */
@AnalyzeClasses(packages = "ar.edu.um.turnos.appointments.reservation", importOptions = DoNotIncludeTests.class)
class ReservationArchitectureTest {

    @ArchTest
    static final ArchRule elDominioNoDependeDeFrameworks = noClasses()
        .that()
        .resideInAPackage("..reservation.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "com.fasterxml.jackson..", "tools.jackson..");

    @ArchTest
    static final ArchRule elDominioYLosPuertosNoConocenLaInfraestructura = noClasses()
        .that()
        .resideInAnyPackage("..reservation.domain..", "..reservation.ports..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..reservation.infrastructure..");
}
