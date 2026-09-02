package com.ganjj.authorization.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Diz qual instância respondeu à requisição.
 *
 * Dentro de um Pod, o Kubernetes coloca o nome do Pod na variável HOSTNAME.
 * Com várias réplicas atrás do mesmo Service, chamar esta rota repetidas vezes
 * mostra nomes diferentes — é assim que se enxerga a distribuição entre elas.
 *
 * Fora do cluster devolve "local".
 */
@RestController
@Tag(name = "Infraestrutura", description = "Rotas de apoio para operação")
public class InstanceController {

    @GetMapping("/instance")
    @Operation(summary = "Nome do Pod que respondeu",
            description = "Usada para observar a distribuição entre réplicas no Kubernetes.")
    public Map<String, String> instance() {
        String hostname = System.getenv().getOrDefault("HOSTNAME", "local");
        return Map.of("pod", hostname);
    }
}
