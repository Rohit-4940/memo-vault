package com.nexmemo.security.service;

import lombok.RequiredArgsConstructor;
import org.jasypt.encryption.StringEncryptor;
import org.springframework.stereotype.Service;
/**
 * @author : Rohit Chapagain
 * @created : 2026-05-10, Sunday
 **/
@Service
@RequiredArgsConstructor
public class EmailConfigEncryptionService {

    private final StringEncryptor jasyptStringEncryptor;

    /** Call before saving smtp_password_encrypted to email_config table */
    public String encrypt(String plainPassword) {
        return jasyptStringEncryptor.encrypt(plainPassword);
    }

    /** Call before using password to build JavaMailSender */
    public String decrypt(String encryptedPassword) {
        return jasyptStringEncryptor.decrypt(encryptedPassword);
    }
}
