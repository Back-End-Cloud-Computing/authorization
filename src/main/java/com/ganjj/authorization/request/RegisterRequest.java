package com.ganjj.authorization.request;

import com.ganjj.authorization.infra.validation.TamanhoEmBytes;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

        @NotBlank(message = "O e-mail é obrigatório.")
        @Email(message = "Informe um e-mail válido.")
        @Size(max = 150, message = "O e-mail deve ter no máximo 150 caracteres.")
        String email,

        @NotBlank(message = "A senha é obrigatória.")
        @Size(min = 8, message = "A senha deve ter ao menos 8 caracteres.")
        // O limite do BCrypt é em bytes: acentos ocupam mais de um, então
        // contar caracteres deixaria passar senha que o algoritmo recusa.
        @TamanhoEmBytes(max = 72, message = "A senha é longa demais (o limite é 72 bytes; "
                + "acentos contam como mais de um).")
        String password) {
}
