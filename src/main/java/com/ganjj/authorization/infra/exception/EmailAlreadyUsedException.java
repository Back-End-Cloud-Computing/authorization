package com.ganjj.authorization.infra.exception;

public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("Já existe uma conta com o e-mail " + email + ".");
    }
}
