package com.ganjj.authorization.service.security;

import java.time.Instant;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ganjj.authorization.repository.RevokedTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Grava a revogação de um refresh token numa transação própria.
 *
 * A transação separada não é detalhe: uma violação de chave primária marca a
 * transação corrente como "somente rollback", e capturar a exceção não desfaz
 * essa marca — o commit falharia mais adiante mesmo com o erro tratado. Isolando
 * a escrita, a disputa pelo token é resolvida sem contaminar a transação de quem
 * chamou.
 */
@Component
@RequiredArgsConstructor
public class RevokedTokenWriter {

    private final RevokedTokenRepository revokedTokenRepository;

    /**
     * Marca o token como revogado.
     *
     * A exceção de chave duplicada <b>não</b> é tratada aqui de propósito: uma
     * violação marca a transação como somente rollback, e capturá-la dentro do
     * próprio método transacional faria o commit falhar mesmo com o erro
     * tratado. Deixando-a atravessar a fronteira, a transação desfaz-se
     * limpamente e quem chamou decide o que fazer.
     *
     * @throws DataIntegrityViolationException se o token já estava revogado
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revogar(String jti, Instant expiracao) {
        revokedTokenRepository.inserir(jti, expiracao, Instant.now());
    }
}
