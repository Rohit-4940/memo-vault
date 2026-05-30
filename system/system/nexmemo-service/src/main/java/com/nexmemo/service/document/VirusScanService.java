package com.nexmemo.service.document;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.net.Socket;

/**
 * @author : Rohit Chapagain
 * @created : 2026-05-10, Sunday
 **/
@Slf4j
@Service
public class VirusScanService {

    @Value("${nexmemo.security.clamav.enabled:false}")
    private boolean enabled;

    @Value("${nexmemo.security.clamav.host:localhost}")
    private String host;

    @Value("${nexmemo.security.clamav.port:3310}")
    private int port;

    @Value("${nexmemo.security.clamav.timeout-ms:10000}")
    private int timeoutMs;

    public ScanResult scan(byte[] fileBytes, String filename) {
        if (!enabled) {
            log.warn("ClamAV disabled — {} not scanned. Enable in prod: nexmemo.security.clamav.enabled=true",
                    filename);
            return new ScanResult("SKIPPED", false);
        }

        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream  in  = socket.getInputStream();

            // ClamAV INSTREAM protocol:
            // Send: "zINSTREAM\0" + [4-byte big-endian length][data] + [4-byte 0x00 terminator]
            out.write("zINSTREAM\0".getBytes());
            out.write(toBytes(fileBytes.length));  // 4-byte big-endian chunk size
            out.write(fileBytes);
            out.write(new byte[]{0, 0, 0, 0});     // zero-length chunk = end of stream
            out.flush();

            // Response: "stream: OK\0" or "stream: {VIRUS_NAME} FOUND\0"
            String response = new String(in.readAllBytes()).trim();
            boolean infected = response.contains("FOUND");

            log.info("ClamAV scan: file={} result={}", filename, infected ? "INFECTED" : "CLEAN");
            return new ScanResult(infected ? "INFECTED" : "CLEAN", infected);

        } catch (IOException e) {
            log.error("ClamAV connection failed ({}:{}): {}", host, port, e.getMessage());
            // Fail-closed: if we can't scan, we reject the file
            return new ScanResult("SCAN_ERROR", true);
        }
    }

    private byte[] toBytes(int value) {
        return new byte[]{
                (byte)(value >>> 24),
                (byte)(value >>> 16),
                (byte)(value >>> 8),
                (byte)(value)
        };
    }

    public record ScanResult(String result, boolean isInfected) {}
}
