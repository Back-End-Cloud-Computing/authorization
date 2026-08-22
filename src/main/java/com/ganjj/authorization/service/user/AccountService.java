package com.ganjj.authorization.service.user;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.domain.user.UserRole;
import com.ganjj.authorization.infra.exception.EmailAlreadyUsedException;
import com.ganjj.authorization.infra.exception.InvalidCredentialsException;
import com.ganjj.authorization.repository.UserRepository;
import com.ganjj.authorization.request.LoginRequest;
import com.ganjj.authorization.request.RegisterRequest;
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
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        UUID userId = tokenService.extractUserId(refreshToken, TokenService.TYPE_REFRESH);
        if (userId == null) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
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
