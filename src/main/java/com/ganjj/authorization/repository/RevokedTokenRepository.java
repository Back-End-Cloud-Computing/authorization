package com.ganjj.authorization.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ganjj.authorization.domain.token.RevokedToken;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, String> {

    /** Remove os registros que já passaram da data em que o token expiraria. */
    @Modifying
    @Query("DELETE FROM RevokedToken t WHERE t.expiresAt < :limite")
    int deleteExpiredBefore(@Param("limite") Instant limite);
}
