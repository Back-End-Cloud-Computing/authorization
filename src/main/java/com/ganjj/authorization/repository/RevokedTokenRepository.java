package com.ganjj.authorization.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ganjj.authorization.domain.token.RevokedToken;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /**
     * Insere a revogação de forma atômica.
     *
     * Usa INSERT direto, e não {@code save()}, de propósito: o {@code save()} de
     * uma entidade com id atribuído faz merge (atualiza se já existir), o que
     * nunca falharia. Aqui queremos que o segundo pedido com o mesmo jti
     * <b>falhe</b> na chave primária — é isso que garante que apenas um pedido
     * consiga usar um refresh token, mesmo que dois cheguem ao mesmo tempo.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException se o jti
     *         já tiver sido revogado
     */
    @Modifying
    @Query(value = "INSERT INTO token_revogado (jti, data_expiracao, data_revogacao) "
            + "VALUES (:jti, :expiracao, :revogacao)", nativeQuery = true)
    void inserir(@Param("jti") String jti,
            @Param("expiracao") Instant expiracao,
            @Param("revogacao") Instant revogacao);

    /** Remove os registros que já passaram da data em que o token expiraria. */
    @Modifying
    @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :limite")
    int deleteExpiredBefore(@Param("limite") Instant limite);
}
