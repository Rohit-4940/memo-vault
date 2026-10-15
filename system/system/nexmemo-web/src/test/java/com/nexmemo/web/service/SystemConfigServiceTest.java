package com.nexmemo.web.service;

import com.nexmemo.core.entity.SystemConfig;
import com.nexmemo.core.repository.SystemConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Phase 4a — secret safety: SMTP credentials must never be returned to the client.
 */
@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock private SystemConfigRepository systemConfigRepository;

    @InjectMocks private SystemConfigService service;

    @Test
    void getEmailConfig_masksSecretValues_butLeavesOthers() {
        SystemConfig host = cfg(1L, "SMTP_HOST", "smtp.gmail.com");
        SystemConfig password = cfg(2L, "SMTP_PASSWORD", "super-secret");
        when(systemConfigRepository.findAll()).thenReturn(List.of(host, password));

        List<SystemConfig> result = service.getEmailConfig();

        SystemConfig maskedPassword = byKey(result, "SMTP_PASSWORD");
        assertThat(maskedPassword.getConfigValue()).isEqualTo("********");
        assertThat(byKey(result, "SMTP_HOST").getConfigValue()).isEqualTo("smtp.gmail.com");
        // the managed entity is untouched (defensive copy)
        assertThat(password.getConfigValue()).isEqualTo("super-secret");
    }

    @Test
    void getEmailConfig_filtersNonSmtpKeys() {
        when(systemConfigRepository.findAll())
                .thenReturn(List.of(cfg(1L, "COMPANY_NAME", "X"), cfg(2L, "SMTP_PORT", "587")));

        List<SystemConfig> result = service.getEmailConfig();

        assertThat(result).extracting(SystemConfig::getConfigKey).containsExactly("SMTP_PORT");
    }

    private static SystemConfig byKey(List<SystemConfig> configs, String key) {
        return configs.stream()
                .filter(c -> key.equals(c.getConfigKey()))
                .findFirst()
                .orElseThrow();
    }

    private static SystemConfig cfg(Long id, String key, String value) {
        SystemConfig config = new SystemConfig();
        config.setId(id);
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setIsActive(true);
        return config;
    }
}
