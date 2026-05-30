package com.nexmemo.web.service;

import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.dto.CreateUserRequest;
import com.nexmemo.security.dto.UserDto;
import com.nexmemo.security.repository.RoleRepository;
import com.nexmemo.security.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDto getUserById(Long id) {
        User user = userRepository.findByIdWithRole(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        return toDto(user);
    }

    public UserDto updateUser(Long id, UserDto dto) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        existing.setName(dto.getName());
        existing.setUsername(dto.getUsername());
        existing.setEmail(dto.getEmail());
        existing.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
        existing.setForcePasswordChange(dto.isForcePasswordChange());
        if (dto.getRoleId() != null) {
            roleRepository.findById(dto.getRoleId()).ifPresent(existing::setRole);
        }
        existing = userRepository.save(existing);
        log.info("User updated: {}", existing.getUsername());
        return toDto(existing);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
        log.info("User deleted: {}", id);
    }

    private UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setStatus(user.getStatus());
        dto.setRoleId(user.getRole() != null ? user.getRole().getId() : null);
        dto.setRoleName(user.getRole() != null ? user.getRole().getRoleName() : null);
        dto.setForcePasswordChange(user.getForcePasswordChange());
        return dto;
    }
}
