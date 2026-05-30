package com.nexmemo.core.repository;

import com.nexmemo.core.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {
    Optional<District> findByName(String name);
    boolean existsByName(String name);
    List<District> findByProvinceId(Long provinceId);
}
