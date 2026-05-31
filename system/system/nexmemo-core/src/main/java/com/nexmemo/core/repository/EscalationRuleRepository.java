package com.nexmemo.core.repository;

import com.nexmemo.core.entity.EscalationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EscalationRuleRepository extends JpaRepository<EscalationRule, Long> {
    List<EscalationRule> findByIsGlobalTrue();
    List<EscalationRule> findByCategoryId(Long categoryId);
}
