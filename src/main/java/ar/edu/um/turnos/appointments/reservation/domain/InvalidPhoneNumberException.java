package ar.edu.um.turnos.appointments.reservation.domain;

public class InvalidPhoneNumberException extends RuntimeException {

    public InvalidPhoneNumberException() {
        // No se repite el número en el mensaje: es un dato personal
        super("El teléfono no es válido: tiene que tener entre 7 y 15 dígitos, con + opcional");
    }
}
