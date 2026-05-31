package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MemoCategoryRepository extends JpaRepository<MemoCategory, Long> {
    Optional<MemoCategory> findByCode(String code);
}
