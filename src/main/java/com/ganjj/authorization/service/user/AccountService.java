package com.ganjj.authorization.service.user;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ganjj.authorization.domain.token.RevokedToken;
import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.domain.user.UserRole;
import com.ganjj.authorization.infra.exception.EmailAlreadyUsedException;
import com.ganjj.authorization.infra.exception.InvalidCredentialsException;
import com.ganjj.authorization.repository.RevokedTokenRepository;
import com.ganjj.authorization.repository.UserRepository;
import com.ganjj.authorization.request.LoginRequest;
import com.ganjj.authorization.request.RegisterRequest;
import com.ganjj.authorization.response.AccountResponse;
import com.ganjj.authorization.response.TokenResponse;
import com.ganjj.authorization.service.security.TokenService;

import lombok.RequiredArgsConstructor;

/**
 * Regras de cadastro e autenticação de conta.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RevokedTokenRepository revokedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /** Cadastro público: toda conta criada por aqui nasce como CLIENTE. */
    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyUsedException(email);
        }

        User user = new User(email, passwordEncoder.encode(request.password()), UserRole.CLIENTE);
        return userRepository.save(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        if (!user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        return issueTokens(user);
    }

    /** Troca um refresh token válido por um novo par de tokens. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        DecodedJWT decoded = tokenService.verifyTyped(refreshToken, TokenService.TYPE_REFRESH);
        if (decoded == null) {
            throw new InvalidCredentialsException();
        }

        // Um token revogado por logout não renova mais nada.
        if (decoded.getId() != null && revokedTokenRepository.existsById(decoded.getId())) {
            throw new InvalidCredentialsException();
        }

        UUID userId = tokenService.parseSubject(decoded);
        if (userId == null) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        // O refresh token usado é queimado e um novo é emitido no lugar: assim um
        // token vazado deixa de valer assim que o dono legítimo renovar.
        revoke(decoded);

        return issueTokens(user);
    }

    /**
     * Invalida o refresh token informado.
     *
     * O token de acesso continua válido até expirar (por padrão, 15 minutos) —
     * é a contrapartida de os outros serviços validarem sem consultar ninguém.
     * Por isso o cliente deve descartar o token de acesso ao sair.
     */
    @Transactional
    public void logout(String refreshToken) {
        DecodedJWT decoded = tokenService.verifyTyped(refreshToken, TokenService.TYPE_REFRESH);

        // Sair com um token já inválido não é erro: o resultado desejado (o token
        // não vale mais) já está valendo.
        if (decoded == null || decoded.getId() == null) {
            return;
        }

        revoke(decoded);
    }

    private void revoke(DecodedJWT decoded) {
        if (revokedTokenRepository.existsById(decoded.getId())) {
            return;
        }
        revokedTokenRepository.save(
                new RevokedToken(decoded.getId(), decoded.getExpiresAt().toInstant()));
    }

    /** Usada pela rota administrativa de listagem. */
    @Transactional(readOnly = true)
    public List<AccountResponse> listAll() {
        return userRepository.findAll().stream().map(AccountResponse::from).toList();
    }

    private TokenResponse issueTokens(User user) {
        return TokenResponse.of(
                tokenService.generateAccessToken(user),
                tokenService.generateRefreshToken(user),
                tokenService.accessTokenSeconds());
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
