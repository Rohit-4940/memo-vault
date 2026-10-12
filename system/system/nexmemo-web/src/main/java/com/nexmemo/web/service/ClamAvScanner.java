package com.nexmemo.web.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Malware scanner for chat attachments backed by clamd (ClamAV daemon).
 *
 * Implements the clamd {@code INSTREAM} protocol over a raw socket — no third-party client
 * library is needed. Policy is fail-closed:
 * <ul>
 *   <li>scanner disabled ({@code nexmemo.security.clamav.enabled=false}) → pass-through;</li>
 *   <li>scanner enabled and reachable → "stream: ok" is clean, "… FOUND" is infected;</li>
 *   <li>scanner enabled and unreachable/timing out → {@link IllegalStateException}, so the
 *       upload is rejected rather than releasing an unscanned file.</li>
 * </ul>
 *
 * @author : NexMemo
 **/
@Slf4j
@Component
public class ClamAvScanner {

    private static final int CHUNK_SIZE = 8192;

    private final boolean enabled;
    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamAvScanner(@Value("${nexmemo.security.clamav.enabled:false}") boolean enabled,
                         @Value("${nexmemo.security.clamav.host:localhost}") String host,
                         @Value("${nexmemo.security.clamav.port:3310}") int port,
                         @Value("${nexmemo.security.clamav.timeout-ms:10000}") int timeoutMs) {
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Scans the given bytes. {@code true} = clean; {@code false} = infected.
     *
     * @throws IllegalStateException when the scanner is enabled but unreachable (fail closed).
     */
    public boolean scan(byte[] content) {
        if (!enabled) {
            return true;
        }
        if (content == null || content.length == 0) {
            return true;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, content.length - offset);
                out.write(toBigEndian(length));
                out.write(content, offset, length);
                out.flush();
            }
            out.write(new byte[]{0, 0, 0, 0});
            out.flush();

            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[512];
            int read;
            while ((read = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, read, StandardCharsets.US_ASCII));
            }
            String result = response.toString().trim();
            boolean clean = result.toLowerCase().startsWith("stream: ok");
            if (!clean) {
                log.warn("ClamAV verdict on uploaded file: {}", result);
            }
            return clean;
        } catch (java.io.IOException e) {
            log.warn("ClamAV scan failed (fail closed): {}", e.getMessage());
            throw new IllegalStateException("Malware scanner unavailable; upload rejected");
        }
    }

    private static byte[] toBigEndian(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
}