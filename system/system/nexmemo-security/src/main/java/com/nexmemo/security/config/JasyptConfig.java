package com.nexmemo.security.config;

import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * @author : Rohit Chapagain
 * @created : 2026-05-10, Sunday
 **/
@Slf4j
@Configuration
@EnableEncryptableProperties
public class JasyptConfig {

    @Value("${jasypt.encryptor.password:#{environment['JASYPT_ENCRYPTOR_PASSWORD']}}")
    private String masterPassword;

    @Bean(name = "jasyptStringEncryptor")
    public StringEncryptor stringEncryptor() {
        if (masterPassword == null || masterPassword.isBlank()) {
            log.warn("SECURITY: JASYPT_ENCRYPTOR_PASSWORD not set — ENC() values won't decrypt. " +
                    "Set env var before starting in production.");
        }

        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        SimpleStringPBEConfig config = new SimpleStringPBEConfig();

        config.setPassword(masterPassword != null ? masterPassword : "dev-only-not-secure");
        config.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        config.setKeyObtentionIterations("1000");
        config.setPoolSize("1");
        config.setSaltGeneratorClassName("org.jasypt.salt.RandomSaltGenerator");
        config.setIvGeneratorClassName("org.jasypt.iv.RandomIvGenerator");
        config.setStringOutputType("base64");

        encryptor.setConfig(config);
        return encryptor;
    }
}
