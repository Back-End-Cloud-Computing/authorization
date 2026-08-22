package com.ganjj.authorization.infra.exception;

/**
 * Usada tanto para e-mail inexistente quanto para senha errada — a mensagem é
 * a mesma nos dois casos, para não revelar quais e-mails estão cadastrados.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos.");
    }
}
