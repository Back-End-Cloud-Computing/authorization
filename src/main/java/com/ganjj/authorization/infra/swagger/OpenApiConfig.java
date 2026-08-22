package com.ganjj.authorization.infra.swagger;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ganjjOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("GANJJ — Authorization Service")
                        .version("1.0.0")
                        .description("Emite e valida os tokens JWT (RS256) usados por todos os "
                                + "microsserviços do GANJJ."))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
