package com.ganjj.authorization.infra.validation;

import java.nio.charset.StandardCharsets;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TamanhoEmBytesValidator implements ConstraintValidator<TamanhoEmBytes, String> {

    private int max;

    @Override
    public void initialize(TamanhoEmBytes anotacao) {
        this.max = anotacao.max();
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        // Campo nulo ou em branco é responsabilidade do @NotBlank.
        if (valor == null) {
            return true;
        }
        return valor.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
