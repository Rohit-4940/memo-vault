package com.nexmemo.security.repository;
import com.nexmemo.core.entity.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long>, JpaSpecificationExecutor<EmailVerificationToken> {
    Optional<EmailVerificationToken> findByTokenAndIsUsedFalse(String token);
}
