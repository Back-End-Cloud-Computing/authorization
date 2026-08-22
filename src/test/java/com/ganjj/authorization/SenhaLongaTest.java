package com.ganjj.authorization;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * O BCrypt recusa segredos com mais de 72 <b>bytes</b>, mas a validação limita
 * 72 <b>caracteres</b>. Uma senha com acentos cabe na validação e estoura no
 * BCrypt — "senhaç" x 12 tem 72 caracteres e 84 bytes em UTF-8.
 */
@SpringBootTest
class SenhaLongaTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void senhaComAcentosNoLimiteNaoPodeDerrubarOCadastro() throws Exception {
        String senha = "senhaç".repeat(12); // 72 caracteres, 84 bytes

        int status = mockMvc().perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "acentos@ganjj.com", "password": "%s"}
                        """.formatted(senha)))
                .andReturn().getResponse().getStatus();

        // Aceitar (201) ou recusar com mensagem clara (400) servem. 500 não.
        if (status != 201 && status != 400) {
            throw new AssertionError(
                    "senha de 72 caracteres / 84 bytes devolveu " + status
                            + " — esperado 201 ou 400");
        }
    }
}
