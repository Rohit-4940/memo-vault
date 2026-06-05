package com.nexmemo.security.repository;
import com.nexmemo.core.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long>, JpaSpecificationExecutor<LoginAttempt> {
    @Query("SELECT COUNT(a) FROM LoginAttempt a WHERE (a.username = :username OR a.ipAddress = :ip) AND a.isSuccess = false AND a.createdDate > :since")
    int countRecentFailures(String username, String ip, LocalDateTime since);
}
