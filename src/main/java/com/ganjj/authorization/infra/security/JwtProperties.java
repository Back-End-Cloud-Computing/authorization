package com.ganjj.authorization.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do JWT, injetada por variáveis de ambiente.
 *
 * Os caminhos das chaves são opcionais: sem eles o serviço gera um par RSA
 * temporário na inicialização, o que serve para rodar local sem preparar nada,
 * mas invalida os tokens a cada restart. Em qualquer ambiente compartilhado,
 * aponte para as chaves reais.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        long accessTokenMinutes,
        long refreshTokenDays,
        String privateKeyPath,
        String publicKeyPath) {
}
