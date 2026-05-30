package com.nexmemo.security.repository;

import com.nexmemo.core.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    boolean existsByBranchCode(String branchCode);
    List<Branch> findByProvinceId(Long provinceId);
    List<Branch> findByDistrictId(Long districtId);
}
