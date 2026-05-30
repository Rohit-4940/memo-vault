package com.nexmemo.web.service;

import com.nexmemo.core.entity.Menu;
import com.nexmemo.core.entity.Role;
import com.nexmemo.core.entity.RolePermissionRights;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.dto.RoleDto;
import com.nexmemo.security.repository.MenuRepository;
import com.nexmemo.security.repository.RolePermissionRightsRepository;
import com.nexmemo.security.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final MenuRepository menuRepository;
    private final RolePermissionRightsRepository rightsRepository;

    @Transactional(readOnly = true)
    public List<RoleDto> getAllRoles() {
        return roleRepository.findByStatus(Status.ACTIVE).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleDto getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));
        return toDto(role);
    }

    public RoleDto createRole(RoleDto dto) {
        if (roleRepository.existsByRoleName(dto.getRoleName())) {
            throw new IllegalArgumentException("Role already exists: " + dto.getRoleName());
        }
        Role role = new Role();
        role.setRoleName(dto.getRoleName().toUpperCase());
        role.setRoleType(dto.getRoleType());
        role.setRoleAccess(dto.getRoleAccess());
        role.setIsRoleApprovable(dto.getIsRoleApprovable() != null && dto.getIsRoleApprovable());
        role.setRoleApprovalLimitAmount(dto.getRoleApprovalLimitAmount());
        role = roleRepository.save(role);

        assignMenusToRole(role, dto.getMenuIds());
        log.info("Role created: {}", role.getRoleName());
        return toDto(role);
    }

    public RoleDto updateRole(Long id, RoleDto dto) {
        Role existing = roleRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Role not found with id: " + id));
        existing.setRoleName(dto.getRoleName().toUpperCase());
        existing.setRoleType(dto.getRoleType());
        existing.setRoleAccess(dto.getRoleAccess());
        existing.setIsRoleApprovable(dto.getIsRoleApprovable() != null && dto.getIsRoleApprovable());
        existing.setRoleApprovalLimitAmount(dto.getRoleApprovalLimitAmount());
        roleRepository.save(existing);

        rightsRepository.deleteByRoleId(id);
        assignMenusToRole(existing, dto.getMenuIds());
        log.info("Role updated: {}", existing.getRoleName());
        return toDto(existing);
    }

    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new EntityNotFoundException("Role not found with id: " + id);
        }
        rightsRepository.deleteByRoleId(id);
        roleRepository.deleteById(id);
        log.info("Role deleted: {}", id);
    }

    private void assignMenusToRole(Role role, List<Long> menuIds) {
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                menuRepository.findById(menuId).ifPresent(menu -> {
                    RolePermissionRights rights = new RolePermissionRights();
                    rights.setRole(role);
                    rights.setMenu(menu);
                    rights.setApiRights(new ArrayList<>());
                    rightsRepository.save(rights);
                });
            }
        }
    }

    private RoleDto toDto(Role role) {
        RoleDto dto = new RoleDto();
        dto.setId(role.getId());
        dto.setRoleName(role.getRoleName());
        dto.setRoleType(role.getRoleType());
        dto.setRoleAccess(role.getRoleAccess());
        dto.setIsRoleApprovable(role.getIsRoleApprovable());
        dto.setRoleApprovalLimitAmount(role.getRoleApprovalLimitAmount());
        dto.setMenuIds(rightsRepository.findByRoleId(role.getId())
                .stream().map(r -> r.getMenu().getId()).collect(Collectors.toList()));
        return dto;
    }
}
