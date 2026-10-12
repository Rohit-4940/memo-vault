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

    /**
     * Resource-based check used by chat authorization: does this user belong to the given branch?
     * Derived query on the ManyToMany {@code branches} collection.
     */
    boolean existsByIdAndBranches_Id(Long id, Long branchId);

    /** Resource-based check for DEPARTMENT chat channels via the user_departments join table. */
    boolean existsByIdAndDepartments_Id(Long id, Long departmentId);

    /** All branch ids a user belongs to — used to scope search results. */
    @Query("SELECT b.id FROM User u JOIN u.branches b WHERE u.id = :userId")
    List<Long> findBranchIdsByUserId(@Param("userId") Long userId);

    /** All department ids a user belongs to — used to scope search results. */
    @Query("SELECT d.id FROM User u JOIN u.departments d WHERE u.id = :userId")
    List<Long> findDepartmentIdsByUserId(@Param("userId") Long userId);

    List<User> findByRole_RoleName(String roleName);

    /** Active users only — used by the chat participant directory (never expose all statuses). */
    List<User> findByStatus(com.nexmemo.core.enums.Status status);

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
