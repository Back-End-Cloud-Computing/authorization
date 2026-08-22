package com.ganjj.authorization.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.infra.security.RsaKeyProvider;
import com.ganjj.authorization.request.LoginRequest;
import com.ganjj.authorization.request.LogoutRequest;
import com.ganjj.authorization.request.RefreshRequest;
import com.ganjj.authorization.request.RegisterRequest;
import com.ganjj.authorization.response.AccountResponse;
import com.ganjj.authorization.response.TokenResponse;
import com.ganjj.authorization.service.user.AccountService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação", description = "Cadastro, login e emissão de tokens do GANJJ")
public class AuthenticationController {

    private final AccountService accountService;
    private final RsaKeyProvider keyProvider;

    @PostMapping("/register")
    @Operation(summary = "Cria uma conta de cliente",
            description = "Cadastro público. Retorna o id que o serviço client usa para amarrar o perfil.")
    public ResponseEntity<AccountResponse> register(@RequestBody @Valid RegisterRequest request) {
        User user = accountService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(user));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e devolve os tokens",
            description = "Envie o accessToken em Authorization: Bearer <token> nas chamadas aos demais serviços.")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(accountService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o token de acesso a partir de um refresh token válido")
    public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid RefreshRequest request) {
        return ResponseEntity.ok(accountService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalida o refresh token",
            description = "O token de acesso continua valendo até expirar — descarte-o no cliente ao sair.")
    public ResponseEntity<Void> logout(@RequestBody @Valid LogoutRequest request) {
        accountService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Dados da conta autenticada")
    public ResponseEntity<AccountResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(AccountResponse.from(user));
    }

    @GetMapping("/accounts")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista as contas cadastradas",
            description = "Restrito a ADMIN. Serve também para conferir que a autorização por papel está valendo.")
    public ResponseEntity<List<AccountResponse>> accounts() {
        return ResponseEntity.ok(accountService.listAll());
    }

    @GetMapping(value = "/public-key", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Chave pública RSA em PEM",
            description = "Consumida uma vez, na inicialização, pelos demais microsserviços — "
                    + "que passam a validar os tokens localmente, sem chamar este serviço.")
    public ResponseEntity<String> publicKey() {
        return ResponseEntity.ok(keyProvider.publicKeyAsPem());
    }
}
