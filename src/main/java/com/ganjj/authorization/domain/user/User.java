package com.ganjj.authorization.domain.user;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Credencial de acesso ao GANJJ.
 *
 * Este serviço é dono apenas do dado de autenticação: e-mail, senha e papel.
 * O perfil do cliente (nome, endereço, contato) pertence ao serviço "client",
 * que referencia esta conta pelo mesmo id.
 */
@Entity
@Table(name = "conta_usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "id_conta", length = 36, updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", length = 150, nullable = false, unique = true)
    private String email;

    /** Sempre armazenada com hash BCrypt — nunca em texto puro. */
    @Column(name = "senha", nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 20, nullable = false)
    private UserRole role;

    @Column(name = "ativo", nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(name = "data_criacao", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "ultimo_login")
    private LocalDateTime lastLogin;

    public User(String email, String encodedPassword, UserRole role) {
        this.email = email;
        this.password = encodedPassword;
        this.role = role;
        this.active = true;
    }

    // --- Contrato do Spring Security -------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (this.role == UserRole.ADMIN) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_CLIENTE"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_CLIENTE"));
    }

    /** O login do GANJJ é feito por e-mail. */
    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.active);
    }
}
