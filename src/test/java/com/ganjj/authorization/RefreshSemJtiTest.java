package com.ganjj.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.ganjj.authorization.infra.security.RsaKeyProvider;

/**
 * Um refresh token legítimo, mas sem a claim "jti", não pode passar.
 *
 * Sem o jti não há como saber se ele foi revogado — aceitar seria abrir mão da
 * checagem de revogação justamente no caso em que ela não pode ser feita.
 */
@SpringBootTest
class RefreshSemJtiTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private RsaKeyProvider keys;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    /** Cria uma conta de verdade e devolve o id — sem isso o fluxo para antes
     *  de chegar na parte que queremos exercitar. */
    private String contaReal(String email) throws Exception {
        String corpo = mockMvc().perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "senhaSegura123"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(corpo).get("id").asString();
    }

    private String refreshSemJti(String contaId) {
        Algorithm algoritmo = Algorithm.RSA256(keys.getPublicKey(), keys.getPrivateKey());
        Instant agora = Instant.now();

        return JWT.create()
                .withIssuer("ganjj-authorization-test")
                .withSubject(contaId)
                .withClaim("typ", "refresh")
                .withIssuedAt(agora)
                .withExpiresAt(agora.plus(7, ChronoUnit.DAYS))
                .sign(algoritmo);
    }

    @Test
    void refreshTokenSemJtiERecusado() throws Exception {
        String token = refreshSemJti(contaReal("semjti@ganjj.com"));

        mockMvc().perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutComTokenSemJtiNaoQuebra() throws Exception {
        String token = refreshSemJti(contaReal("semjti2@ganjj.com"));

        mockMvc().perform(post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(token)))
                .andExpect(status().isNoContent());
    }
}
