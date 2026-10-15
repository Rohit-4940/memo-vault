package com.nexmemo.web.service;

import com.nexmemo.web.config.CompressionProperties;
import com.nexmemo.web.utils.FileKind;
import com.nexmemo.web.utils.FileSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

/**
 * Structurally inspects an uploaded archive <b>before</b> the system does anything else with it,
 * enforcing the archive-bomb and path-traversal guardrails configured under
 * {@code nexmemo.compression.guardrails}.
 *
 * <p>Nothing is ever written to disk: entries are streamed and counted in memory with hard caps, so
 * a malicious input can exhaust neither the file system nor the heap. Violations raise
 * {@link ArchiveSecurityException}, which the worker turns into a failed job with a clear reason —
 * a hostile archive is a normal, auditable outcome, not a crash.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
public class SafeArchiveService {

    private static final int HEAD_BYTES = 512;
    /** Windows drive prefix, e.g. {@code C:} — an absolute path we refuse to see inside an archive. */
    private static final Pattern DRIVE_PREFIX = Pattern.compile("^[A-Za-z]:.*");

    private final CompressionProperties properties;

    public SafeArchiveService(CompressionProperties properties) {
        this.properties = properties;
    }

    /**
     * Result of a successful inspection — safe to persist as a guardrail summary.
     */
    public record Result(int entryCount, long uncompressedBytes, long compressedBytes) {
        public int ratioPercent() {
            return compressedBytes > 0 ? (int) Math.min(Integer.MAX_VALUE, uncompressedBytes * 100 / compressedBytes) : 0;
        }
    }

    public Result inspect(byte[] data, FileKind kind) {
        CompressionProperties.Guardrails g = properties.getGuardrails();
        Budget budget = new Budget(g.getMaxEntries(), g.getMaxUncompressedBytes());
        try {
            walk(data, kind, 0, budget, g);
        } catch (IOException e) {
            throw new ArchiveSecurityException("Archive could not be read: " + e.getMessage());
        }
        long uncompressed = budget.consumed;
        if (data.length > 0) {
            long ratio = uncompressed / Math.max(1, data.length);
            if (ratio > g.getMaxCompressionRatio()) {
                throw new ArchiveSecurityException(
                        "Archive expands " + ratio + "x (limit " + g.getMaxCompressionRatio() + "x) — rejected as a decompression bomb");
            }
        }
        log.debug("Archive inspection ok: entries={} uncompressed={} compressed={}",
                budget.entries, uncompressed, data.length);
        return new Result(budget.entries, uncompressed, data.length);
    }

    private void walk(byte[] data, FileKind kind, int depth, Budget budget, CompressionProperties.Guardrails g) throws IOException {
        switch (kind) {
            case ZIP -> walkZip(data, depth, budget, g);
            case TAR -> walkTar(new ByteArrayInputStream(data), depth, budget, g);
            case GZIP -> walkGzip(data, depth, budget, g);
            default -> throw new ArchiveSecurityException("Not a supported archive type: " + kind);
        }
    }

    private void walkZip(byte[] data, int depth, Budget budget, CompressionProperties.Guardrails g) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(data))) {
            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                budget.newEntry();
                validateEntryName(entry.getName());
                handleEntry(zis, depth, budget, g);
            }
        }
    }

    private void walkTar(InputStream raw, int depth, Budget budget, CompressionProperties.Guardrails g) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(raw)) {
            TarArchiveEntry entry;
            while ((entry = tis.getNextTarEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                budget.newEntry();
                validateEntryName(entry.getName());
                handleEntry(tis, depth, budget, g);
            }
        }
    }

    private void walkGzip(byte[] data, int depth, Budget budget, CompressionProperties.Guardrails g) throws IOException {
        budget.newEntry();
        try (GzipCompressorInputStream gis = new GzipCompressorInputStream(new ByteArrayInputStream(data))) {
            handleEntry(gis, depth, budget, g);
        }
    }

    /**
     * Reads one entry: consumes its bytes against the budget, and if the entry is itself an archive
     * within the permitted nesting depth, descends into it.
     */
    private void handleEntry(InputStream entryStream, int depth, Budget budget, CompressionProperties.Guardrails g) throws IOException {
        byte[] head = readHead(entryStream, budget);
        FileKind nested = FileSignatureValidator.detect(head);
        if (nested.isArchive() && depth + 1 <= g.getMaxNestingDepth()) {
            byte[] full = readRemainder(head, entryStream, budget);
            walk(full, nested, depth + 1, budget, g);
        } else if (nested.isArchive() && depth + 1 > g.getMaxNestingDepth()) {
            throw new ArchiveSecurityException(
                    "Archive nesting exceeds the permitted depth of " + g.getMaxNestingDepth());
        } else {
            drain(entryStream, budget);
        }
    }

    private byte[] readHead(InputStream in, Budget budget) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream(HEAD_BYTES);
        byte[] buffer = new byte[Math.min(HEAD_BYTES, 256)];
        while (head.size() < HEAD_BYTES) {
            int toRead = Math.min(buffer.length, HEAD_BYTES - head.size());
            int read = in.read(buffer, 0, toRead);
            if (read == -1) {
                break;
            }
            budget.consume(read);
            head.write(buffer, 0, read);
        }
        return head.toByteArray();
    }

    private byte[] readRemainder(byte[] head, InputStream in, Budget budget) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(head.length + 1024);
        out.write(head);
        drain(in, budget, out);
        return out.toByteArray();
    }

    private void drain(InputStream in, Budget budget) throws IOException {
        drain(in, budget, null);
    }

    private void drain(InputStream in, Budget budget, ByteArrayOutputStream sink) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            budget.consume(read);
            if (sink != null) {
                sink.write(buffer, 0, read);
            }
        }
    }

    /**
     * Rejects any entry name that could escape the extraction root on a future consumer: absolute
     * paths, drive-qualified paths, and any {@code ..} segment.
     */
    private void validateEntryName(String name) {
        if (name == null || name.isBlank()) {
            throw new ArchiveSecurityException("Archive contains an entry with no name");
        }
        String normalised = name.replace('\\', '/');
        if (normalised.startsWith("/") || DRIVE_PREFIX.matcher(normalised).matches()) {
            throw new ArchiveSecurityException("Archive entry uses an absolute path: " + name);
        }
        for (String segment : normalised.split("/")) {
            if ("..".equals(segment)) {
                throw new ArchiveSecurityException("Archive entry attempts path traversal: " + name);
            }
        }
    }

    /** Mutable caps shared across the whole (possibly nested) traversal. */
    private static final class Budget {
        private final int maxEntries;
        private final long maxBytes;
        private int entries;
        private long consumed;

        private Budget(int maxEntries, long maxBytes) {
            this.maxEntries = maxEntries;
            this.maxBytes = maxBytes;
        }

        void newEntry() {
            if (++entries > maxEntries) {
                throw new ArchiveSecurityException("Archive has more than " + maxEntries + " entries — rejected");
            }
        }

        void consume(int bytes) {
            consumed += bytes;
            if (consumed > maxBytes) {
                throw new ArchiveSecurityException(
                        "Archive expands beyond the " + maxBytes + "-byte limit — rejected as a decompression bomb");
            }
        }
    }
}
