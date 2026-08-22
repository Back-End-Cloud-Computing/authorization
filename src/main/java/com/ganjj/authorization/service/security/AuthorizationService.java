package com.ganjj.authorization.service.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.ganjj.authorization.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * Ponte entre o Spring Security e a tabela de contas. O "username" do GANJJ
 * é o e-mail.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada: " + email));
    }
}
