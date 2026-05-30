package com.nexmemo.web.service;

import com.nexmemo.core.entity.Department;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.DepartmentRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Department getDepartmentById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));
    }

    public Department createDepartment(Department department) {
        department.setStatus(department.getStatus() != null ? department.getStatus() : Status.ACTIVE);
        department = departmentRepository.save(department);
        log.info("Department created: {}", department.getName());
        return department;
    }

    public Department updateDepartment(Long id, Department dto) {
        Department existing = departmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + id));
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        existing = departmentRepository.save(existing);
        log.info("Department updated: {}", existing.getName());
        return existing;
    }

    public void deleteDepartment(Long id) {
        if (!departmentRepository.existsById(id)) {
            throw new EntityNotFoundException("Department not found with id: " + id);
        }
        departmentRepository.deleteById(id);
        log.info("Department deleted: {}", id);
    }
}
