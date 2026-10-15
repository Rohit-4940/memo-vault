package com.nexmemo.web.service;

import com.nexmemo.core.entity.SystemConfig;
import com.nexmemo.core.repository.SystemConfigRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    private static final String SECRET_MASK = "********";

    @Transactional(readOnly = true)
    public List<SystemConfig> getAllConfigs() {
        return systemConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public SystemConfig getConfigById(Long id) {
        return systemConfigRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SystemConfig not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public SystemConfig getConfigByKey(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new EntityNotFoundException("SystemConfig not found with key: " + key));
    }

    public SystemConfig createConfig(SystemConfig config) {
        String configKey = config.getConfigKey();
        systemConfigRepository.findByConfigKey(configKey).ifPresent(existing -> {
            throw new IllegalArgumentException("SystemConfig already exists with key: " + configKey);
        });
        config.setIsActive(config.getIsActive() != null && config.getIsActive());
        SystemConfig saved = systemConfigRepository.save(config);
        log.info("SystemConfig created: {}", saved.getConfigKey());
        return saved;
    }

    public SystemConfig updateConfig(Long id, SystemConfig config) {
        SystemConfig existing = systemConfigRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SystemConfig not found with id: " + id));
        existing.setConfigKey(config.getConfigKey());
        existing.setConfigValue(config.getConfigValue());
        existing.setDescription(config.getDescription());
        existing.setDataType(config.getDataType());
        if (config.getIsActive() != null) existing.setIsActive(config.getIsActive());
        existing = systemConfigRepository.save(existing);
        log.info("SystemConfig updated: {}", existing.getConfigKey());
        return existing;
    }

    public SystemConfig updateConfigByKey(String key, SystemConfig config) {
        SystemConfig existing = systemConfigRepository.findByConfigKey(key)
                .orElseThrow(() -> new EntityNotFoundException("SystemConfig not found with key: " + key));
        existing.setConfigValue(config.getConfigValue());
        existing.setDescription(config.getDescription());
        existing.setDataType(config.getDataType());
        if (config.getIsActive() != null) existing.setIsActive(config.getIsActive());
        existing = systemConfigRepository.save(existing);
        log.info("SystemConfig updated by key: {}", key);
        return existing;
    }

    public void deleteConfig(Long id) {
        if (!systemConfigRepository.existsById(id)) {
            throw new EntityNotFoundException("SystemConfig not found with id: " + id);
        }
        systemConfigRepository.deleteById(id);
        log.info("SystemConfig deleted: {}", id);
    }

    @Transactional(readOnly = true)
    public String getConfigValue(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElseThrow(() -> new EntityNotFoundException("SystemConfig not found with key: " + key));
    }

    @Transactional(readOnly = true)
    public List<SystemConfig> getEmailConfig() {
        return systemConfigRepository.findAll().stream()
                .filter(c -> c.getConfigKey() != null && c.getConfigKey().toLowerCase().contains("smtp"))
                .map(this::maskSecrets)
                .collect(Collectors.toList());
    }

    /**
     * Phase 4a: never hand credentials back to the client. Secret-named keys are returned as
     * detached copies with their value masked, so SMTP passwords / tokens are not exposed via the
     * admin config endpoint.
     */
    private SystemConfig maskSecrets(SystemConfig config) {
        if (!isSecretKey(config.getConfigKey())) {
            return config;
        }
        SystemConfig masked = new SystemConfig();
        masked.setId(config.getId());
        masked.setConfigKey(config.getConfigKey());
        masked.setConfigValue(SECRET_MASK);
        masked.setDescription(config.getDescription());
        masked.setDataType(config.getDataType());
        masked.setIsActive(config.getIsActive());
        return masked;
    }

    private static boolean isSecretKey(String key) {
        if (key == null) {
            return false;
        }
        String upper = key.toUpperCase();
        return upper.contains("PASSWORD") || upper.contains("SECRET")
                || upper.contains("TOKEN") || upper.contains("CREDENTIAL");
    }

    public void updateEmailConfig(Map<String, String> emailSettings) {
        emailSettings.forEach((key, value) -> {
            String configKey = "SMTP_" + key.toUpperCase();
            systemConfigRepository.findByConfigKey(configKey).ifPresentOrElse(
                config -> {
                    config.setConfigValue(value);
                    systemConfigRepository.save(config);
                    log.debug("Email config updated: {}", configKey);
                },
                () -> log.warn("Email config key not found: {}", configKey)
            );
        });
    }

    public void createDefaultConfigs() {
        createIfNotPresent("DEFAULT_FISCAL_YEAR", "", "STRING", "Default fiscal year identifier");
        createIfNotPresent("SMTP_HOST", "", "STRING", "SMTP server hostname");
        createIfNotPresent("SMTP_PORT", "587", "STRING", "SMTP server port");
        createIfNotPresent("SMTP_USERNAME", "", "STRING", "SMTP server username");
        createIfNotPresent("SMTP_PASSWORD", "", "STRING", "SMTP server password");
        createIfNotPresent("COMPANY_NAME", "", "STRING", "Company/organization name");
        createIfNotPresent("COMPANY_ADDRESS", "", "STRING", "Company/organization address");
        log.info("Default system configs seeded");
    }

    private void createIfNotPresent(String key, String value, String dataType, String description) {
        if (systemConfigRepository.findByConfigKey(key).isEmpty()) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDataType(dataType);
            config.setDescription(description);
            config.setIsActive(true);
            systemConfigRepository.save(config);
            log.debug("Default config created: {}", key);
        }
    }
}
