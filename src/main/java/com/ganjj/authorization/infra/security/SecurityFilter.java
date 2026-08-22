package com.ganjj.authorization.infra.security;

import java.io.IOException;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.repository.UserRepository;
import com.ganjj.authorization.service.security.TokenService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Autentica a requisição a partir do header Authorization.
 *
 * Aqui, dentro do serviço dono das contas, vale consultar o banco: garante que
 * uma conta desativada perca acesso na hora. Nos demais microsserviços do GANJJ
 * esse passo não existe — lá a validação usa apenas as claims do token, sem
 * consultar banco nenhum e sem chamar este serviço.
 */
@Component
@RequiredArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = recoverToken(request);

        if (token != null) {
            UUID userId = tokenService.extractUserId(token, TokenService.TYPE_ACCESS);

            if (userId != null) {
                userRepository.findById(userId)
                        .filter(User::isEnabled)
                        .ifPresent(this::authenticate);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user) {
        var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String recoverToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header == null || !header.startsWith(PREFIX)) {
            return null;
        }
        return header.substring(PREFIX.length()).trim();
    }
}
