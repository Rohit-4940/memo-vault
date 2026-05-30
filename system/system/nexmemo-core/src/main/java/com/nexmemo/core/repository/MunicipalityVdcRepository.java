package com.nexmemo.core.repository;

import com.nexmemo.core.entity.MunicipalityVdc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MunicipalityVdcRepository extends JpaRepository<MunicipalityVdc, Long> {
    List<MunicipalityVdc> findByDistrictId(Long districtId);
    boolean existsByName(String name);
}
