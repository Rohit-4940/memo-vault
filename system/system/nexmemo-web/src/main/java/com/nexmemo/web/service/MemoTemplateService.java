package com.nexmemo.web.service;

import com.nexmemo.core.entity.Department;
import com.nexmemo.core.entity.MemoTemplate;
import com.nexmemo.core.entity.MemoType;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.DepartmentRepository;
import com.nexmemo.core.repository.MemoTemplateRepository;
import com.nexmemo.core.repository.MemoTypeRepository;
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
public class MemoTemplateService {

    private final MemoTemplateRepository memoTemplateRepository;
    private final MemoTypeRepository memoTypeRepository;
    private final DepartmentRepository departmentRepository;

    @Transactional(readOnly = true)
    public List<MemoTemplate> getAllTemplates() {
        return memoTemplateRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MemoTemplate getTemplateById(Long id) {
        return memoTemplateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MemoTemplate not found with id: " + id));
    }

    public MemoTemplate createTemplate(MemoTemplate template) {
        Long memoTypeId = template.getMemoType() != null ? template.getMemoType().getId() : null;
        if (memoTypeId != null) {
            MemoType mt = memoTypeRepository.findById(memoTypeId)
                    .orElseThrow(() -> new EntityNotFoundException("MemoType not found with id: " + memoTypeId));
            template.setMemoType(mt);
        }
        Long deptId = template.getDepartment() != null ? template.getDepartment().getId() : null;
        if (deptId != null) {
            Department dept = departmentRepository.findById(deptId)
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + deptId));
            template.setDepartment(dept);
        }
        template.setStatus(template.getStatus() != null ? template.getStatus() : Status.ACTIVE);
        MemoTemplate saved = memoTemplateRepository.save(template);
        log.info("MemoTemplate created: {}", saved.getName());
        return saved;
    }

    public MemoTemplate updateTemplate(Long id, MemoTemplate template) {
        MemoTemplate existing = memoTemplateRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MemoTemplate not found with id: " + id));
        existing.setName(template.getName());
        existing.setDescription(template.getDescription());
        existing.setContent(template.getContent());
        if (template.getMemoType() != null && template.getMemoType().getId() != null) {
            MemoType memoType = memoTypeRepository.findById(template.getMemoType().getId())
                    .orElseThrow(() -> new EntityNotFoundException("MemoType not found with id: " + template.getMemoType().getId()));
            existing.setMemoType(memoType);
        }
        if (template.getDepartment() != null && template.getDepartment().getId() != null) {
            Department department = departmentRepository.findById(template.getDepartment().getId())
                    .orElseThrow(() -> new EntityNotFoundException("Department not found with id: " + template.getDepartment().getId()));
            existing.setDepartment(department);
        }
        if (template.getStatus() != null) existing.setStatus(template.getStatus());
        existing = memoTemplateRepository.save(existing);
        log.info("MemoTemplate updated: {}", existing.getName());
        return existing;
    }

    public void deleteTemplate(Long id) {
        if (!memoTemplateRepository.existsById(id)) {
            throw new EntityNotFoundException("MemoTemplate not found with id: " + id);
        }
        memoTemplateRepository.deleteById(id);
        log.info("MemoTemplate deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public List<MemoTemplate> findByDepartmentId(Long deptId) {
        if (!departmentRepository.existsById(deptId)) {
            throw new EntityNotFoundException("Department not found with id: " + deptId);
        }
        return memoTemplateRepository.findByDepartmentId(deptId);
    }

    @Transactional(readOnly = true)
    public List<MemoTemplate> findByMemoTypeId(Long typeId) {
        if (!memoTypeRepository.existsById(typeId)) {
            throw new EntityNotFoundException("MemoType not found with id: " + typeId);
        }
        return memoTemplateRepository.findByMemoTypeId(typeId);
    }
}
