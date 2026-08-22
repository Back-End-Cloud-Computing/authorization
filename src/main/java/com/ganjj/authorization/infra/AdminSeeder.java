package com.ganjj.authorization.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ganjj.authorization.domain.user.User;
import com.ganjj.authorization.domain.user.UserRole;
import com.ganjj.authorization.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Cria a conta ADMIN inicial a partir de variáveis de ambiente.
 *
 * O cadastro público sempre gera contas CLIENTE, então sem este passo o papel
 * ADMIN existiria no código mas nenhuma conta o teria — e as rotas de admin dos
 * outros serviços do GANJJ ficariam impossíveis de testar.
 *
 * É idempotente: se a conta já existe, nada acontece.
 */
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL/ADMIN_PASSWORD não configurados: nenhuma conta ADMIN foi criada.");
            return;
        }

        String email = adminEmail.trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            log.info("Conta ADMIN {} já existe — nada a fazer.", email);
            return;
        }

        userRepository.save(new User(email, passwordEncoder.encode(adminPassword), UserRole.ADMIN));
        log.info("Conta ADMIN criada: {}", email);
    }
}
