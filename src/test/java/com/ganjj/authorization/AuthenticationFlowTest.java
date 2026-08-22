package com.ganjj.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.security.web.FilterChainProxy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Percorre o caminho real de uso: cadastro, login e acesso autenticado.
 *
 * O teste mais importante aqui é o último: ele prova a decisão de arquitetura
 * do projeto — que um serviço com apenas a chave pública consegue validar o
 * token, sem chamar o Authorization e sem poder emitir tokens novos.
 */
@SpringBootTest
class AuthenticationFlowTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    private String registerAndLogin(String email) throws Exception {
        MockMvc mvc = mockMvc();

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "senhaSegura123"}
                        """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.role").value("CLIENTE"));

        String body = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "senhaSegura123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("accessToken").asText();
    }

    @Test
    void cadastraAutenticaEAcessaRotaProtegida() throws Exception {
        String token = registerAndLogin("cliente@ganjj.com");

        mockMvc().perform(get("/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("cliente@ganjj.com"))
                .andExpect(jsonPath("$.role").value("CLIENTE"));
    }

    @Test
    void rotaProtegidaSemTokenRetorna401() throws Exception {
        mockMvc().perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void senhaErradaNaoAutentica() throws Exception {
        MockMvc mvc = mockMvc();

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "outro@ganjj.com", "password": "senhaSegura123"}
                        """))
                .andExpect(status().isCreated());

        mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "outro@ganjj.com", "password": "senhaErrada999"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void emailDuplicadoRetorna409() throws Exception {
        MockMvc mvc = mockMvc();
        String payload = """
                {"email": "repetido@ganjj.com", "password": "senhaSegura123"}
                """;

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    void emailInvalidoRetorna400() throws Exception {
        mockMvc().perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "nao-e-email", "password": "senhaSegura123"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    /**
     * A decisão de arquitetura, verificada de ponta a ponta: os demais
     * microsserviços do GANJJ (Go, Python, Node, C#, PHP) recebem apenas o PEM
     * da chave pública e conseguem validar o token localmente.
     */
    @Test
    void tokenPodeSerValidadoSomenteComAChavePublica() throws Exception {
        String token = registerAndLogin("validacao@ganjj.com");

        String pem = mockMvc().perform(get("/auth/public-key"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        RSAPublicKey publicKey = parsePem(pem);

        // Algorithm.RSA256 com chave privada nula: só permite verificar, nunca assinar.
        DecodedJWT decoded = JWT.require(Algorithm.RSA256(publicKey, null))
                .withIssuer("ganjj-authorization-test")
                .build()
                .verify(token);

        assertThat(decoded.getClaim("email").asString()).isEqualTo("validacao@ganjj.com");
        assertThat(decoded.getClaim("role").asString()).isEqualTo("CLIENTE");
        assertThat(decoded.getAlgorithm()).isEqualTo("RS256");
        assertThat(decoded.getSubject()).isNotBlank();
    }

    @Test
    void refreshTokenGeraNovoAccessToken() throws Exception {
        MockMvc mvc = mockMvc();

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "refresh@ganjj.com", "password": "senhaSegura123"}
                        """))
                .andExpect(status().isCreated());

        String loginBody = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "refresh@ganjj.com", "password": "senhaSegura123"}
                        """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode login = objectMapper.readTree(loginBody);
        String refreshToken = login.get("refreshToken").asText();

        mvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "%s"}
                        """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    /** Um refresh token não vale como token de acesso. */
    @Test
    void refreshTokenNaoAbreRotaProtegida() throws Exception {
        MockMvc mvc = mockMvc();

        mvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "tipos@ganjj.com", "password": "senhaSegura123"}
                        """))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "tipos@ganjj.com", "password": "senhaSegura123"}
                        """))
                .andReturn().getResponse().getContentAsString();

        String refreshToken = objectMapper.readTree(body).get("refreshToken").asText();

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized());
    }

    private RSAPublicKey parsePem(String pem) throws Exception {
        String base64 = pem.replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }
}
