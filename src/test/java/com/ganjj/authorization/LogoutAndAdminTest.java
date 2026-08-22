package com.ganjj.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cobre logout (revogação do refresh token), autorização por papel e CORS.
 */
@SpringBootTest
class LogoutAndAdminTest {

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

    private JsonNode registerAndLogin(String email) throws Exception {
        MockMvc mvc = mockMvc();
        String payload = """
                {"email": "%s", "password": "senhaSegura123"}
                """.formatted(email);

        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        String body = mvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body);
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mockMvc().perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    // --- Logout ----------------------------------------------------------

    @Test
    void logoutImpedeQueORefreshTokenSejaUsadoDeNovo() throws Exception {
        MockMvc mvc = mockMvc();
        String refreshToken = registerAndLogin("logout@ganjj.com").get("refreshToken").asString();

        String corpo = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        // Antes do logout, o refresh funciona.
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        // Depois de renovar, o token antigo já não vale (é queimado no uso).
        String novoRefresh = login("logout@ganjj.com", "senhaSegura123")
                .get("refreshToken").asString();
        String novoCorpo = """
                {"refreshToken": "%s"}
                """.formatted(novoRefresh);

        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(novoCorpo))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(novoCorpo))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenUsadoUmaVezNaoServeDeNovo() throws Exception {
        MockMvc mvc = mockMvc();
        String refreshToken = registerAndLogin("rotacao@ganjj.com").get("refreshToken").asString();
        String corpo = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isOk());

        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
    }

    /** Sair duas vezes, ou com um token inválido, não é erro. */
    @Test
    void logoutRepetidoOuInvalidoNaoQuebra() throws Exception {
        MockMvc mvc = mockMvc();
        String refreshToken = registerAndLogin("logout2@ganjj.com").get("refreshToken").asString();
        String corpo = """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);

        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNoContent());
        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNoContent());

        mvc.perform(post("/auth/logout").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken": "nao-e-um-token"}
                        """))
                .andExpect(status().isNoContent());
    }

    // --- Autorização por papel -------------------------------------------

    @Test
    void adminCriadoNaInicializacaoConsegueListarContas() throws Exception {
        String token = login("admin@ganjj.com", "adminSegura123").get("accessToken").asString();

        mockMvc().perform(get("/auth/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").exists());
    }

    @Test
    void clienteNaoAcessaRotaDeAdmin() throws Exception {
        String token = registerAndLogin("comum@ganjj.com").get("accessToken").asString();

        mockMvc().perform(get("/auth/accounts").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void rotaDeAdminSemTokenRetorna401() throws Exception {
        mockMvc().perform(get("/auth/accounts"))
                .andExpect(status().isUnauthorized());
    }

    // --- CORS -------------------------------------------------------------

    @Test
    void preflightDoFrontendEAceito() throws Exception {
        mockMvc().perform(options("/auth/login")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void origemDesconhecidaERecusada() throws Exception {
        mockMvc().perform(options("/auth/login")
                .header("Origin", "http://site-nao-autorizado.com")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }
}
