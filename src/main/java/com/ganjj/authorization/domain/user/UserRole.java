package com.ganjj.authorization.domain.user;

/**
 * Papéis reconhecidos pelo GANJJ. O papel viaja dentro do token JWT, na claim
 * "role", e é o que os demais microsserviços usam para autorizar cada rota.
 */
public enum UserRole {
    CLIENTE,
    ADMIN
}
