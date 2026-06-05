package com.nexmemo.security.repository;

import com.nexmemo.core.entity.DeviceRegistry;
import com.nexmemo.core.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.List;
import java.util.Optional;
/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
public interface DeviceRegistryRepository extends JpaRepository<DeviceRegistry, Long>, JpaSpecificationExecutor<DeviceRegistry> {
    Optional<DeviceRegistry> findByUserAndDeviceFingerprint(User user, String fingerprint);
    List<DeviceRegistry> findByUser(User user);
}
