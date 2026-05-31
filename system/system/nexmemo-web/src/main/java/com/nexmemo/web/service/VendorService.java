package com.nexmemo.web.service;

import com.nexmemo.core.entity.User;
import com.nexmemo.core.entity.Vendor;
import com.nexmemo.core.enums.Status;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.core.repository.VendorRepository;
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
public class VendorService {

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Vendor> getAll() {
        return vendorRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Vendor getById(Long id) {
        return vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with id: " + id));
    }

    public Vendor create(Vendor vendor, Long userId) {
        if (vendor.getStatus() == null) {
            vendor.setStatus(Status.ACTIVE);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));
        vendor.setCreatedByUser(user);
        vendor = vendorRepository.save(vendor);
        log.info("Vendor created: {} ({})", vendor.getVendorName(), vendor.getVendorCode());
        return vendor;
    }

    public Vendor update(Long id, Vendor dto) {
        Vendor existing = vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with id: " + id));
        existing.setVendorName(dto.getVendorName());
        existing.setVendorCode(dto.getVendorCode());
        existing.setContactPerson(dto.getContactPerson());
        existing.setEmail(dto.getEmail());
        existing.setPhone(dto.getPhone());
        existing.setAddress(dto.getAddress());
        existing.setPanNumber(dto.getPanNumber());
        existing.setBankAccount(dto.getBankAccount());
        existing.setBankName(dto.getBankName());
        existing.setPaymentTerms(dto.getPaymentTerms());
        existing.setNotes(dto.getNotes());
        if (dto.getStatus() != null) existing.setStatus(dto.getStatus());
        existing = vendorRepository.save(existing);
        log.info("Vendor updated: {} ({})", existing.getVendorName(), existing.getVendorCode());
        return existing;
    }

    public void delete(Long id) {
        Vendor vendor = vendorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Vendor not found with id: " + id));
        vendor.setStatus(Status.DELETED);
        vendorRepository.save(vendor);
        log.info("Vendor soft-deleted: {} ({})", vendor.getVendorName(), vendor.getVendorCode());
    }

    @Transactional(readOnly = true)
    public List<Vendor> search(String query) {
        return vendorRepository.findByVendorNameContainingIgnoreCase(query);
    }
}
