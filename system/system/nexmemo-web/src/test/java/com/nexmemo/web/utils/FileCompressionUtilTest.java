package com.nexmemo.web.utils;

import com.nexmemo.core.enums.CompressionType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the low-level archive helpers: entry-name sanitisation (zip-slip defence), deflate
 * levels and the integrity re-read used before a download is advertised.
 */
class FileCompressionUtilTest {

    private static final byte[] PAYLOAD = "the quick brown fox jumps over the lazy dog".repeat(50)
            .getBytes(StandardCharsets.UTF_8);

    @Test
    void sanitizeStripsDirectoryAndTraversalSegments() {
        assertThat(FileCompressionUtil.sanitizeEntryName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileCompressionUtil.sanitizeEntryName("/etc/shadow")).isEqualTo("shadow");
        assertThat(FileCompressionUtil.sanitizeEntryName("C:\\Windows\\system32\\evil.dll")).isEqualTo("evil.dll");
        assertThat(FileCompressionUtil.sanitizeEntryName("nested/dir/report.pdf")).isEqualTo("report.pdf");
    }

    @Test
    void sanitizeFallsBackWhenNothingUsableRemains() {
        assertThat(FileCompressionUtil.sanitizeEntryName(null)).isEqualTo("file");
        assertThat(FileCompressionUtil.sanitizeEntryName("   ")).isEqualTo("file");
        assertThat(FileCompressionUtil.sanitizeEntryName("..")).isEqualTo("file");
        assertThat(FileCompressionUtil.sanitizeEntryName("../")).isEqualTo("file");
    }

    @Test
    void everyTypeRoundTripsAndVerifies() throws Exception {
        for (CompressionType type : CompressionType.values()) {
            byte[] out = FileCompressionUtil.compress(type, PAYLOAD, "../../weird name.txt", 9);
            assertThat(out).isNotEmpty();
            assertThat(FileCompressionUtil.verifyReadable(type, out))
                    .as("readable %s", type).isTrue();
        }
    }

    @Test
    void higherLevelDoesNotProduceLargerArchive() throws Exception {
        byte[] fast = FileCompressionUtil.compress(CompressionType.GZIP, PAYLOAD, "data.txt", 1);
        byte[] max = FileCompressionUtil.compress(CompressionType.GZIP, PAYLOAD, "data.txt", 9);
        assertThat(max.length).isLessThanOrEqualTo(fast.length);
    }

    @Test
    void verifyReadableRejectsCorruptData() {
        assertThat(FileCompressionUtil.verifyReadable(CompressionType.ZIP, new byte[]{1, 2, 3, 4})).isFalse();
        assertThat(FileCompressionUtil.verifyReadable(CompressionType.GZIP, "not gzip".getBytes())).isFalse();
    }

    @Test
    void sha256IsStableAndDistinct() {
        String a = FileCompressionUtil.sha256(PAYLOAD);
        assertThat(a).isEqualTo(FileCompressionUtil.sha256(PAYLOAD));
        assertThat(a).hasSize(64);
        assertThat(a).isNotEqualTo(FileCompressionUtil.sha256("other".getBytes()));
    }

    @Test
    void outputFileNameUsesSanitisedBase() {
        assertThat(FileCompressionUtil.outputFileName("/tmp/../report.pdf", CompressionType.ZIP))
                .isEqualTo("report.pdf.zip");
    }
}
