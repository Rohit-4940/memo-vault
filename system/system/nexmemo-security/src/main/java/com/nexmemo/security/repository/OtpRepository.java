package com.nexmemo.security.repository;
import com.nexmemo.core.entity.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {
    @Query("SELECT o FROM OtpVerification o WHERE o.user.id = :userId AND o.otpType = :type AND o.isUsed = false ORDER BY o.createdDate DESC LIMIT 1")
    Optional<OtpVerification> findLatestActiveOtp(Long userId, String type);
    @Modifying
    @Query("UPDATE OtpVerification o SET o.isUsed = true WHERE o.user.id = :userId AND o.otpType = :type AND o.isUsed = false")
    void invalidatePreviousOtps(Long userId, String type);
}
