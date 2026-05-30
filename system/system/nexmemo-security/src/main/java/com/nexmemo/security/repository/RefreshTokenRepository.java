package com.nexmemo.security.repository;

import com.nexmemo.core.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT rt FROM RefreshToken rt JOIN FETCH rt.user WHERE rt.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.id = :userId AND rt.deviceFingerprint = :fingerprint")
    void revokeByUserIdAndFingerprint(Long userId, String fingerprint);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.id = :userId")
    void revokeAllByUserId(Long userId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.user.id = :userId AND rt.isRevoked = false")
    void revokeAllActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true WHERE rt.id IN (" +
           "  SELECT rt2.id FROM RefreshToken rt2 WHERE rt2.user.id = :userId" +
           "  AND rt2.deviceFingerprint = :fingerprint AND rt2.isRevoked = false" +
           ")")
    void revokeAllActiveByUserAndFingerprint(@Param("userId") Long userId,
                                              @Param("fingerprint") String fingerprint);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.lastUsedAt = :now WHERE rt.tokenHash = :tokenHash")
    void updateLastUsed(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    long countByUserIdAndIsRevokedFalse(Long userId);

    @Query("SELECT CASE WHEN COUNT(rt) > 0 THEN true ELSE false END FROM RefreshToken rt " +
           "WHERE rt.user.id = :userId AND rt.isRevoked = false")
    boolean hasActiveTokens(@Param("userId") Long userId);
}
