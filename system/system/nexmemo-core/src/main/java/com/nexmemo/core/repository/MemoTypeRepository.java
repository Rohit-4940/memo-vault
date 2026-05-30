package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MemoType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemoTypeRepository extends JpaRepository<MemoType, Long> {
}
