package ar.edu.um.turnos.appointments.reservation.domain;

import java.util.regex.Pattern;

/**
 * Teléfono del paciente, limpio como lo valida la cátedra (sección 15.5 de la referencia):
 * se sacan espacios, guiones, paréntesis y puntos, y tiene que quedar un + opcional y de 7 a
 * 15 dígitos. Se valida acá también para no gastar un intento con un número que la cátedra
 * va a rechazar.
 */
public record PhoneNumber(String value) {
    private static final Pattern VALID = Pattern.compile("^\\+?[0-9]{7,15}$");

    public static PhoneNumber of(String raw) {
        String clean = raw == null ? "" : raw.replaceAll("[\\s\\-().]", "");
        if (!VALID.matcher(clean).matches()) {
            throw new InvalidPhoneNumberException();
        }
        return new PhoneNumber(clean);
    }
}
