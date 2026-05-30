package com.nexmemo.web.service;

import com.nexmemo.core.entity.Branch;
import com.nexmemo.core.entity.District;
import com.nexmemo.core.entity.MunicipalityVdc;
import com.nexmemo.core.entity.Province;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.dto.BranchDto;
import com.nexmemo.core.repository.DistrictRepository;
import com.nexmemo.core.repository.MunicipalityVdcRepository;
import com.nexmemo.core.repository.ProvinceRepository;
import com.nexmemo.security.repository.BranchRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final MunicipalityVdcRepository municipalityVdcRepository;

    @Transactional(readOnly = true)
    public List<BranchDto> getAllBranches() {
        return branchRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BranchDto getBranchById(Long id) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found with id: " + id));
        return toDto(branch);
    }

    public BranchDto createBranch(BranchDto dto) {
        if (branchRepository.existsByBranchCode(dto.getBranchCode())) {
            throw new IllegalArgumentException("Branch code already exists: " + dto.getBranchCode());
        }
        Branch branch = toEntity(dto);
        branch.setStatus(dto.getStatus() != null ? dto.getStatus() : Status.ACTIVE);
        branch = branchRepository.save(branch);
        log.info("Branch created: {} ({})", branch.getName(), branch.getBranchCode());
        return toDto(branch);
    }

    public BranchDto updateBranch(Long id, BranchDto dto) {
        Branch existing = branchRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found with id: " + id));
        if (!existing.getBranchCode().equals(dto.getBranchCode())
                && branchRepository.existsByBranchCode(dto.getBranchCode())) {
            throw new IllegalArgumentException("Branch code already exists: " + dto.getBranchCode());
        }
        existing.setName(dto.getName());
        existing.setBranchCode(dto.getBranchCode());
        existing.setStreetName(dto.getStreetName());
        existing.setWardNumber(dto.getWardNumber());
        existing.setBranchEmail(dto.getBranchEmail());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        if (dto.getProvinceId() != null) {
            existing.setProvince(provinceRepository.findById(dto.getProvinceId()).orElse(null));
        }
        if (dto.getDistrictId() != null) {
            existing.setDistrict(districtRepository.findById(dto.getDistrictId()).orElse(null));
        }
        if (dto.getMunicipalityVdcId() != null) {
            existing.setMunicipalityVdc(municipalityVdcRepository.findById(dto.getMunicipalityVdcId()).orElse(null));
        }
        existing = branchRepository.save(existing);
        log.info("Branch updated: {} ({})", existing.getName(), existing.getBranchCode());
        return toDto(existing);
    }

    public void deleteBranch(Long id) {
        if (!branchRepository.existsById(id)) {
            throw new EntityNotFoundException("Branch not found with id: " + id);
        }
        branchRepository.deleteById(id);
        log.info("Branch deleted: {}", id);
    }

    private BranchDto toDto(Branch branch) {
        BranchDto dto = new BranchDto();
        dto.setId(branch.getId());
        dto.setName(branch.getName());
        dto.setBranchCode(branch.getBranchCode());
        dto.setStatus(branch.getStatus());
        dto.setStreetName(branch.getStreetName());
        dto.setWardNumber(branch.getWardNumber());
        dto.setBranchEmail(branch.getBranchEmail());
        if (branch.getProvince() != null) {
            dto.setProvinceId(branch.getProvince().getId());
            dto.setProvinceName(branch.getProvince().getName());
        }
        if (branch.getDistrict() != null) {
            dto.setDistrictId(branch.getDistrict().getId());
            dto.setDistrictName(branch.getDistrict().getName());
        }
        if (branch.getMunicipalityVdc() != null) {
            dto.setMunicipalityVdcId(branch.getMunicipalityVdc().getId());
            dto.setMunicipalityVdcName(branch.getMunicipalityVdc().getName());
        }
        return dto;
    }

    private Branch toEntity(BranchDto dto) {
        Branch branch = new Branch();
        branch.setName(dto.getName());
        branch.setBranchCode(dto.getBranchCode());
        branch.setStreetName(dto.getStreetName());
        branch.setWardNumber(dto.getWardNumber());
        branch.setBranchEmail(dto.getBranchEmail());
        if (dto.getProvinceId() != null) {
            provinceRepository.findById(dto.getProvinceId()).ifPresent(branch::setProvince);
        }
        if (dto.getDistrictId() != null) {
            districtRepository.findById(dto.getDistrictId()).ifPresent(branch::setDistrict);
        }
        if (dto.getMunicipalityVdcId() != null) {
            municipalityVdcRepository.findById(dto.getMunicipalityVdcId()).ifPresent(branch::setMunicipalityVdc);
        }
        return branch;
    }
}
