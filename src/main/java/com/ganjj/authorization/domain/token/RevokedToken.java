package com.ganjj.authorization.domain.token;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Refresh token invalidado por um logout.
 *
 * Guardamos apenas o identificador do token (jti), nunca o token em si — quem
 * lesse a tabela não conseguiria se passar por ninguém.
 *
 * O campo expiresAt existe para a limpeza automática: depois que o token
 * expiraria por conta própria, o registro não serve mais para nada.
 */
@Entity
@Table(name = "token_revogado")
@Getter
@Setter
@NoArgsConstructor
public class RevokedToken {

    @Id
    @Column(name = "jti", length = 36, nullable = false, updatable = false)
    private String jti;

    @Column(name = "data_expiracao", nullable = false)
    private Instant expiresAt;

    @Column(name = "data_revogacao", nullable = false)
    private Instant revokedAt;

    public RevokedToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
        this.revokedAt = Instant.now();
    }
}
