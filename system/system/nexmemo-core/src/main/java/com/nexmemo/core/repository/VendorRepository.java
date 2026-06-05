package com.nexmemo.core.repository;

import com.nexmemo.core.entity.Vendor;
import com.nexmemo.core.enums.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VendorRepository extends JpaRepository<Vendor, Long>, JpaSpecificationExecutor<Vendor> {
    List<Vendor> findByStatus(Status status);
    Optional<Vendor> findByVendorCode(String vendorCode);
    List<Vendor> findByVendorNameContainingIgnoreCase(String vendorName);
}
