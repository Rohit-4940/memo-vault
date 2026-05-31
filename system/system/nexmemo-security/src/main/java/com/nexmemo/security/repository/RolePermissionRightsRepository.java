package com.nexmemo.security.repository;

import com.nexmemo.core.entity.RolePermissionRights;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RolePermissionRightsRepository extends JpaRepository<RolePermissionRights, Long> {
    List<RolePermissionRights> findByRoleId(Long roleId);
    List<RolePermissionRights> findByMenuId(Long menuId);
    void deleteByRoleId(Long roleId);
    void deleteByMenuId(Long menuId);
}
