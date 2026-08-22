package com.ganjj.authorization.infra.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Limita o tamanho do texto em <b>bytes</b> (UTF-8), não em caracteres.
 *
 * O BCrypt recusa segredos com mais de 72 bytes. Validar por caractere deixaria
 * passar uma senha acentuada dentro do limite aparente — "senhaç" repetido 12
 * vezes tem 72 caracteres e 84 bytes — e o erro só apareceria lá embaixo, como
 * falha inesperada.
 */
@Documented
@Constraint(validatedBy = TamanhoEmBytesValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface TamanhoEmBytes {

    String message() default "O texto excede o tamanho máximo permitido.";

    int max();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
