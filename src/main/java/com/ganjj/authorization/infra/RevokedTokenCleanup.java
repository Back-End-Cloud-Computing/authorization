package com.ganjj.authorization.infra;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ganjj.authorization.repository.RevokedTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Limpa da lista de revogados os tokens que já expiraram sozinhos.
 *
 * Sem isso a tabela cresceria para sempre, guardando registros que não mudam
 * mais nenhuma decisão — um token expirado já é recusado pela verificação.
 */
@Component
@RequiredArgsConstructor
public class RevokedTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RevokedTokenCleanup.class);

    private final RevokedTokenRepository revokedTokenRepository;

    @Scheduled(cron = "${jwt.revoked-cleanup-cron:0 0 3 * * *}")
    @Transactional
    public void removeExpired() {
        int removed = revokedTokenRepository.deleteExpiredBefore(Instant.now());
        if (removed > 0) {
            log.info("Limpeza da lista de tokens revogados: {} registro(s) removido(s).", removed);
        }
    }
}
