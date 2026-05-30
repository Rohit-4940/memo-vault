package com.nexmemo.security.repository;

import com.nexmemo.core.entity.EmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Repository
public interface EmailConfigRepository extends JpaRepository<EmailConfig, Long> {
    Optional<EmailConfig> findFirstByIsActiveTrueOrderByIdDesc();
    Optional<EmailConfig> findByConfigKey(String configKey);
}
