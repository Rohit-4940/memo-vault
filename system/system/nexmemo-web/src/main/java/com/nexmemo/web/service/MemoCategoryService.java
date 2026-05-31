package com.nexmemo.web.service;

import com.nexmemo.core.entity.MemoCategory;
import com.nexmemo.core.entity.CategoryDocumentRequirement;
import com.nexmemo.core.repository.MemoCategoryRepository;
import com.nexmemo.core.repository.CategoryDocumentRequirementRepository;
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
public class MemoCategoryService {

    private final MemoCategoryRepository memoCategoryRepository;
    private final CategoryDocumentRequirementRepository documentRequirementRepository;

    @Transactional(readOnly = true)
    public List<MemoCategory> getAllCategories() {
        return memoCategoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MemoCategory getCategoryById(Long id) {
        return memoCategoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MemoCategory not found: " + id));
    }

    public MemoCategory createCategory(MemoCategory category) {
        return memoCategoryRepository.save(category);
    }

    public MemoCategory updateCategory(Long id, MemoCategory updated) {
        MemoCategory cat = getCategoryById(id);
        cat.setName(updated.getName());
        cat.setCode(updated.getCode());
        cat.setDescription(updated.getDescription());
        cat.setSlaHours(updated.getSlaHours());
        cat.setIsActive(updated.getIsActive());
        return memoCategoryRepository.save(cat);
    }

    public void deleteCategory(Long id) {
        MemoCategory cat = getCategoryById(id);
        cat.setIsActive(false);
        memoCategoryRepository.save(cat);
    }

    @Transactional(readOnly = true)
    public List<CategoryDocumentRequirement> getDocumentRequirements(Long categoryId) {
        return documentRequirementRepository.findByCategoryId(categoryId);
    }

    public CategoryDocumentRequirement addDocumentRequirement(CategoryDocumentRequirement req) {
        return documentRequirementRepository.save(req);
    }

    public void removeDocumentRequirement(Long id) {
        documentRequirementRepository.deleteById(id);
    }
}
