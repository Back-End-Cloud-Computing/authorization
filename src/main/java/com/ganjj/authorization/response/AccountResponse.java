package com.ganjj.authorization.response;

import java.time.LocalDateTime;
import java.util.UUID;

import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.domain.user.UserRole;

/**
 * Dados públicos da conta. Nunca inclui a senha.
 *
 * O "id" é a chave que o serviço client (e os demais) usam para amarrar seus
 * próprios registros a esta conta.
 */
public record AccountResponse(
        UUID id,
        String email,
        UserRole role,
        boolean active,
        LocalDateTime createdAt) {

    public static AccountResponse from(User user) {
        return new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getRole(),
                Boolean.TRUE.equals(user.getActive()),
                user.getCreatedAt());
    }
}
