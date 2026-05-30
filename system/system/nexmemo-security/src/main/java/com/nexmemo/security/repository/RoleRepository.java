package com.nexmemo.security.repository;

import com.nexmemo.core.entity.Role;
import com.nexmemo.core.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
    List<Role> findByStatus(Status status);
    boolean existsByRoleName(String roleName);
}
