package com.ganjj.authorization.service.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.infra.security.JwtProperties;
import com.ganjj.authorization.infra.security.RsaKeyProvider;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * Emite e verifica os tokens JWT do GANJJ.
 *
 * A assinatura usa RS256: aqui, com a chave privada, para assinar; nos demais
 * serviços, com a chave pública, apenas para verificar. É o que permite que
 * cada microsserviço valide o token localmente, sem chamar este serviço a cada
 * requisição.
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    /** Distingue o token de acesso do token de renovação. */
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_EMAIL = "email";

    private final JwtProperties properties;
    private final RsaKeyProvider keys;

    private Algorithm algorithm;
    private JWTVerifier verifier;

    @PostConstruct
    void init() {
        this.algorithm = Algorithm.RSA256(keys.getPublicKey(), keys.getPrivateKey());
        this.verifier = JWT.require(algorithm).withIssuer(properties.issuer()).build();
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(properties.issuer())
                .withSubject(user.getId().toString())
                .withClaim(CLAIM_EMAIL, user.getEmail())
                .withClaim(CLAIM_ROLE, user.getRole().name())
                .withClaim(CLAIM_TYPE, TYPE_ACCESS)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES))
                .sign(algorithm);
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return JWT.create()
                .withIssuer(properties.issuer())
                .withSubject(user.getId().toString())
                .withClaim(CLAIM_TYPE, TYPE_REFRESH)
                .withIssuedAt(now)
                .withExpiresAt(now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS))
                .sign(algorithm);
    }

    /**
     * Verifica assinatura, emissor e validade.
     *
     * @return o token decodificado, ou null se for inválido ou expirado.
     */
    public DecodedJWT verify(String token) {
        try {
            return verifier.verify(token);
        } catch (JWTVerificationException e) {
            return null;
        }
    }

    /** Id da conta guardado no "sub", ou null se o token não servir. */
    public UUID extractUserId(String token, String expectedType) {
        DecodedJWT decoded = verify(token);
        if (decoded == null) {
            return null;
        }
        String type = decoded.getClaim(CLAIM_TYPE).asString();
        if (!expectedType.equals(type)) {
            return null;
        }
        try {
            return UUID.fromString(decoded.getSubject());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public long accessTokenSeconds() {
        return properties.accessTokenMinutes() * 60;
    }
}
