package com.nexmemo.security.repository;

import com.nexmemo.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.id = :id")
    Optional<User> findByIdWithRole(@Param("id") Long id);
    @Query("SELECT u FROM User u JOIN FETCH u.role WHERE u.username = :username")
    Optional<User> findByUsernameWithRole(@Param("username") String username);

    Optional<User> findByResetPasswordToken(String resetPasswordToken);

    List<User> findByRole_RoleName(String roleName);

    List<User> findByRoleId(Long roleId);

    /**
     * Find all active users whose role is approvable and has sufficient approval limit.
     * Used by ApprovalEngineService to find next approver without loading ALL users.
     */
    @Query("""
            SELECT u FROM User u JOIN FETCH u.role r
            WHERE u.status = 'ACTIVE'
              AND r.roleType IN ('APPROVAL', 'COMMITTEE', 'ADMIN', 'SUPER_ADMIN')
              AND r.isRoleApprovable = true
              AND (r.roleApprovalLimitAmount IS NULL OR r.roleApprovalLimitAmount >= :amount)
            ORDER BY r.roleType
            """)
    List<User> findActiveApproversByMinLimit(@Param("amount") java.math.BigDecimal amount);

    /**
     * Fallback: find any active ADMIN/SUPER_ADMIN who can approve.
     */
    @Query("""
            SELECT u FROM User u JOIN FETCH u.role r
            WHERE u.status = 'ACTIVE'
              AND r.roleType IN ('ADMIN', 'SUPER_ADMIN')
            ORDER BY r.roleType
            """)
    List<User> findActiveAdminApprovers();
}
