package com.nexmemo.web.service;

import com.nexmemo.core.entity.MemoType;
import com.nexmemo.core.enums.Status;
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
public class MemoTypeService {

    private final MemoTypeRepository memoTypeRepository;

    @Transactional(readOnly = true)
    public List<MemoType> getAllMemoTypes() {
        return memoTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public MemoType getMemoTypeById(Long id) {
        return memoTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MemoType not found with id: " + id));
    }

    public MemoType createMemoType(MemoType memoType) {
        memoType.setStatus(memoType.getStatus() != null ? memoType.getStatus() : Status.ACTIVE);
        memoType = memoTypeRepository.save(memoType);
        log.info("MemoType created: {}", memoType.getName());
        return memoType;
    }

    public MemoType updateMemoType(Long id, MemoType dto) {
        MemoType existing = memoTypeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("MemoType not found with id: " + id));
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        existing = memoTypeRepository.save(existing);
        log.info("MemoType updated: {}", existing.getName());
        return existing;
    }

    public void deleteMemoType(Long id) {
        if (!memoTypeRepository.existsById(id)) {
            throw new EntityNotFoundException("MemoType not found with id: " + id);
        }
        memoTypeRepository.deleteById(id);
        log.info("MemoType deleted: {}", id);
    }
}
