package com.nexmemo.security.service;

import com.nexmemo.core.entity.EmailConfig;
import com.nexmemo.security.repository.EmailConfigRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.util.Properties;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailConfigRepository emailConfigRepository;
    private final TemplateEngine templateEngine;
    private final JavaMailSender defaultMailSender;

    @Value("${nexmemo.email.fallback.from-address:no-reply@nexmemo.com}")
    private String fallbackFrom;

    @Value("${nexmemo.email.fallback.from-name:NexMemo System}")
    private String fallbackFromName;

    // ── Email triggers ────────────────────────────────────────────────────────

    @Async
    public void sendAccountCreatedEmail(String to, String name, String username, String password) {
        Context ctx = new Context();
        ctx.setVariable("name",     name);
        ctx.setVariable("username", username);
        ctx.setVariable("password", password);
        ctx.setVariable("appUrl",   "http://localhost:4202");
        send(to, "NexMemo — Your Account Has Been Created", "email/account-created", ctx);
    }

    @Async
    public void sendWelcomeEmail(String to, String name, String token) {
        Context ctx = new Context();
        ctx.setVariable("name",       name);
        ctx.setVariable("setupLink",  buildLink("/set-password?token=" + token));
        ctx.setVariable("expiryHours", 24);
        send(to, "Welcome to NexMemo — Set Your Password", "email/welcome", ctx);
    }

    @Async
    public void sendOtpEmail(String to, String name, String otp, int expiryMinutes) {
        Context ctx = new Context();
        ctx.setVariable("name",          name);
        ctx.setVariable("otp",           otp);
        ctx.setVariable("expiryMinutes", expiryMinutes);
        send(to, "NexMemo — Your Login Code", "email/otp", ctx);
    }

    @Async
    public void sendPasswordResetEmail(String to, String name, String token) {
        Context ctx = new Context();
        ctx.setVariable("name",        name);
        ctx.setVariable("resetLink",   buildLink("/reset-password?token=" + token));
        ctx.setVariable("expiryHours", 24);
        send(to, "NexMemo — Password Reset", "email/password-reset", ctx);
    }

    @Async
    public void sendPasswordChangedNotification(String to, String name, String ipAddress) {
        Context ctx = new Context();
        ctx.setVariable("name",      name);
        ctx.setVariable("ipAddress", ipAddress);
        send(to, "NexMemo — Your Password Was Changed", "email/password-changed", ctx);
    }

    @Async
    public void sendAccountLockedNotification(String to, String name, int minutes) {
        Context ctx = new Context();
        ctx.setVariable("name",    name);
        ctx.setVariable("minutes", minutes);
        send(to, "NexMemo — Account Temporarily Locked", "email/account-locked", ctx);
    }

    @Async
    public void sendSuspiciousLoginAlert(String to, String name, String ip, String time) {
        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("ip",   ip);
        ctx.setVariable("time", time);
        send(to, "NexMemo — Security Alert: Suspicious Login", "email/suspicious-login", ctx);
    }

    // ── Core send logic ───────────────────────────────────────────────────────

    private void send(String to, String subject, String template, Context ctx) {
        try {
            JavaMailSender sender  = resolveMailSender();
            String         from    = resolveFrom();

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, fallbackFromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(templateEngine.process(template, ctx), true);
            sender.send(message);

            log.info("Email sent: to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Email send failed: to={} subject={} error={}", to, subject, e.getMessage());
        }
    }

    /**
     * Prefer active DB email config (admin-configured).
     * Fall back to application.yml Gmail config.
     */
    private JavaMailSender resolveMailSender() {
        return emailConfigRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .map(this::buildSender)
                .orElse(defaultMailSender);
    }

    private String resolveFrom() {
        return emailConfigRepository.findFirstByIsActiveTrueOrderByIdDesc()
                .map(EmailConfig::getFromAddress)
                .orElse(fallbackFrom);
    }

    /**
     * Builds a JavaMailSender from DB-stored config.
     * Gmail settings: host=smtp.gmail.com, port=587, TLS=true
     */
    private JavaMailSender buildSender(EmailConfig cfg) {
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(cfg.getSmtpHost());
        s.setPort(cfg.getSmtpPort());
        s.setUsername(cfg.getSmtpUsername());
        s.setPassword(cfg.getSmtpPasswordEncrypted()); // Jasypt decrypts automatically
        Properties p = s.getJavaMailProperties();
        p.put("mail.transport.protocol",   "smtp");
        p.put("mail.smtp.auth",            "true");
        p.put("mail.smtp.starttls.enable", String.valueOf(cfg.getUseTls()));
        p.put("mail.smtp.starttls.required", String.valueOf(cfg.getUseTls()));
        p.put("mail.smtp.connectiontimeout", "5000");
        p.put("mail.smtp.timeout",           "5000");
        p.put("mail.smtp.writetimeout",      "5000");
        return s;
    }

    private String buildLink(String path) {
        return "https://app.nexmemo.com" + path;  // override in dev via @Value
    }
}
