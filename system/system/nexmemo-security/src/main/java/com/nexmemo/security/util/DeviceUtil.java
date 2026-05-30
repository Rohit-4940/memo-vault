package com.nexmemo.security.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
/**
 * @author : Rohit Chapagain
 * @created : 2026-05-09, Saturday
 **/
@Component
public class DeviceUtil {

    private static final String X_DEVICE_ID = "X-Device-Id";
    private static final String X_MAC_ADDRESS = "X-Mac-Address";
    public String extractDeviceId(HttpServletRequest request) {
        return request.getHeader(X_DEVICE_ID);
    }
    public String extractMacAddress(HttpServletRequest request) {
        return request.getHeader(X_MAC_ADDRESS);
    }

    public String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
    public String extractUserAgent(HttpServletRequest request) {
        return request.getHeader("User-Agent");
    }

    public String buildFingerprint(HttpServletRequest request) {
        String raw = String.join("|",
                safeHeader(request, X_DEVICE_ID),
                safeHeader(request, "User-Agent"),
                extractIp(request)
        );
        return sha256(raw);
    }
    private String safeHeader(HttpServletRequest req, String name) {
        String value = req.getHeader(name);
        return value != null ? value : "unknown";
    }
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
