package com.nexmemo.web.service;

import com.nexmemo.web.config.CompressionProperties;
import com.nexmemo.web.utils.FileKind;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Guardrail tests for {@link SafeArchiveService}: decompression bombs, entry floods and zip-slip
 * entry names must all be rejected before the bytes reach the worker.
 */
class SafeArchiveServiceTest {

    private CompressionProperties props(int maxEntries, long maxUncompressed, int maxRatio, int maxDepth) {
        CompressionProperties p = new CompressionProperties();
        CompressionProperties.Guardrails g = p.getGuardrails();
        g.setMaxEntries(maxEntries);
        g.setMaxUncompressedBytes(maxUncompressed);
        g.setMaxCompressionRatio(maxRatio);
        g.setMaxNestingDepth(maxDepth);
        return p;
    }

    @Test
    void acceptsCleanZipAndReportsTotals() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 10_000_000, 200, 1));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("a.txt", "hello".getBytes(StandardCharsets.UTF_8));
        entries.put("dir/b.txt", "world!!".getBytes(StandardCharsets.UTF_8));

        SafeArchiveService.Result result = service.inspect(zip(entries), FileKind.ZIP);

        assertThat(result.entryCount()).isEqualTo(2);
        assertThat(result.uncompressedBytes()).isEqualTo(5 + 7);
    }

    @Test
    void rejectsPathTraversalEntries() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 10_000_000, 200, 1));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("../../etc/passwd", "x".getBytes());

        assertThatThrownBy(() -> service.inspect(zip(entries), FileKind.ZIP))
                .isInstanceOf(ArchiveSecurityException.class)
                .hasMessageContaining("path traversal");
    }

    @Test
    void rejectsAbsoluteEntryNames() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 10_000_000, 200, 1));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("/etc/shadow", "x".getBytes());

        assertThatThrownBy(() -> service.inspect(zip(entries), FileKind.ZIP))
                .isInstanceOf(ArchiveSecurityException.class)
                .hasMessageContaining("absolute path");
    }

    @Test
    void rejectsEntryFlood() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(3, 10_000_000, 1000, 1));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            entries.put("f" + i + ".txt", "x".getBytes());
        }

        assertThatThrownBy(() -> service.inspect(zip(entries), FileKind.ZIP))
                .isInstanceOf(ArchiveSecurityException.class)
                .hasMessageContaining("entries");
    }

    @Test
    void rejectsUncompressedSizeBomb() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 1024, 10_000, 1));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("big.bin", new byte[5 * 1024 * 1024]);

        assertThatThrownBy(() -> service.inspect(zip(entries), FileKind.ZIP))
                .isInstanceOf(ArchiveSecurityException.class)
                .hasMessageContaining("decompression bomb");
    }

    @Test
    void rejectsHighCompressionRatioBomb() throws Exception {
        // Plenty of byte headroom, but the ratio heuristic must still catch it.
        SafeArchiveService service = new SafeArchiveService(props(100, 200_000_000, 10, 1));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("zeros.bin", new byte[5 * 1024 * 1024]);

        assertThatThrownBy(() -> service.inspect(zip(entries), FileKind.ZIP))
                .isInstanceOf(ArchiveSecurityException.class)
                .hasMessageContaining("bomb");
    }

    @Test
    void rejectsNestedArchiveBeyondDepthZero() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 50_000_000, 10_000, 0));
        byte[] inner = zip(Map.of("a.txt", "hi".getBytes()));
        byte[] outer = zip(Map.of("inner.zip", inner));

        assertThatThrownBy(() -> service.inspect(outer, FileKind.ZIP))
                .isInstanceOf(ArchiveSecurityException.class)
                .hasMessageContaining("nesting");
    }

    @Test
    void descendsOneLevelWhenAllowed() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 50_000_000, 10_000, 1));
        byte[] inner = zip(Map.of("a.txt", "hi".getBytes()));
        byte[] outer = zip(Map.of("inner.zip", inner));

        SafeArchiveService.Result result = service.inspect(outer, FileKind.ZIP);

        assertThat(result.entryCount()).isEqualTo(2);
    }

    @Test
    void inspectsGzipStream() throws Exception {
        SafeArchiveService service = new SafeArchiveService(props(100, 10_000_000, 200, 1));
        byte[] payload = "payload-bytes".getBytes(StandardCharsets.UTF_8);

        SafeArchiveService.Result result = service.inspect(gzip(payload), FileKind.GZIP);

        assertThat(result.entryCount()).isEqualTo(1);
        assertThat(result.uncompressedBytes()).isEqualTo(payload.length);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setSize(e.getValue().length);
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(out)) {
            gzos.write(data);
        }
        return out.toByteArray();
    }
}
