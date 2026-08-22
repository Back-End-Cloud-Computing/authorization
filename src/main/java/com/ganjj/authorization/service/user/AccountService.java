package com.ganjj.authorization.service.user;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.domain.user.UserRole;
import com.ganjj.authorization.infra.exception.EmailAlreadyUsedException;
import com.ganjj.authorization.infra.exception.InvalidCredentialsException;
import com.ganjj.authorization.repository.UserRepository;
import com.ganjj.authorization.request.LoginRequest;
import com.ganjj.authorization.request.RegisterRequest;
import com.ganjj.authorization.response.AccountResponse;
import com.ganjj.authorization.response.TokenResponse;
import com.ganjj.authorization.service.security.RevokedTokenWriter;
import com.ganjj.authorization.service.security.TokenService;

import lombok.RequiredArgsConstructor;

/**
 * Regras de cadastro e autenticação de conta.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RevokedTokenWriter revokedTokenWriter;
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

    /**
     * Troca um refresh token válido por um novo par de tokens.
     *
     * Sem @Transactional de propósito: a única escrita é a revogação, que tem a
     * própria transação. Envolver tudo numa transação externa faria a violação
     * de chave — usada aqui como arbitragem — marcá-la como somente rollback.
     */
    public TokenResponse refresh(String refreshToken) {
        DecodedJWT decoded = tokenService.verifyTyped(refreshToken, TokenService.TYPE_REFRESH);
        if (decoded == null) {
            throw new InvalidCredentialsException();
        }

        // Sem o jti não há como saber se o token foi revogado. Recusar é o
        // único caminho seguro: aceitar seria abrir mão da checagem justamente
        // quando ela não pode ser feita.
        String jti = decoded.getId();
        if (jti == null) {
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

        // Queimar o token é o que decide quem pode renovar. A gravação é a
        // própria disputa: se dois pedidos chegarem com o mesmo token, apenas um
        // consegue inserir o jti e o outro esbarra na chave primária. Conferir
        // antes e gravar depois deixaria uma janela para os dois passarem.
        if (!tentarRevogar(jti, decoded.getExpiresAt().toInstant())) {
            // Já usado — por um logout, por uma renovação anterior, ou por um
            // pedido concorrente que chegou primeiro.
            throw new InvalidCredentialsException();
        }

        return issueTokens(user);
    }

    /**
     * Invalida o refresh token informado.
     *
     * O token de acesso continua válido até expirar (por padrão, 2 horas) —
     * é a contrapartida de os outros serviços validarem sem consultar ninguém.
     * Por isso o cliente deve descartar o token de acesso ao sair.
     *
     * Sem @Transactional pelo mesmo motivo do refresh.
     */
    public void logout(String refreshToken) {
        DecodedJWT decoded = tokenService.verifyTyped(refreshToken, TokenService.TYPE_REFRESH);

        // Sair com um token já inválido não é erro: o resultado desejado (o token
        // não vale mais) já está valendo.
        if (decoded == null || decoded.getId() == null) {
            return;
        }

        // Já revogado não é erro: o resultado desejado já vale.
        tentarRevogar(decoded.getId(), decoded.getExpiresAt().toInstant());
    }

    /**
     * Tenta queimar o refresh token.
     *
     * O tratamento fica aqui, fora da transação do writer: capturar a violação
     * dentro dela faria o commit falhar mesmo com o erro tratado.
     *
     * @return true se esta chamada foi a que revogou; false se o token já
     *         estava revogado.
     */
    private boolean tentarRevogar(String jti, Instant expiracao) {
        try {
            revokedTokenWriter.revogar(jti, expiracao);
            return true;
        } catch (DataIntegrityViolationException e) {
            return false;
        }
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
