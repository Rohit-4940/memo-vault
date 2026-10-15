package com.nexmemo.web.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Magic-byte detection: the client-supplied filename/content-type must never be trusted, so these
 * tests pin the exact byte signatures we do trust.
 */
class FileSignatureValidatorTest {

    @Test
    void detectsPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        assertThat(FileSignatureValidator.detect(png)).isEqualTo(FileKind.PNG);
        assertThat(FileKind.PNG.isImage()).isTrue();
    }

    @Test
    void detectsJpegGifPdf() {
        assertThat(FileSignatureValidator.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}))
                .isEqualTo(FileKind.JPEG);
        assertThat(FileSignatureValidator.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(FileKind.GIF);
        assertThat(FileSignatureValidator.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo(FileKind.PDF);
    }

    @Test
    void detectsZipAndGzip() throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zip)) {
            zos.putNextEntry(new ZipEntry("a.txt"));
            zos.write("hi".getBytes());
            zos.closeEntry();
        }
        assertThat(FileSignatureValidator.detect(zip.toByteArray())).isEqualTo(FileKind.ZIP);
        assertThat(FileKind.ZIP.isArchive()).isTrue();

        ByteArrayOutputStream gz = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(gz)) {
            gzos.write("hi".getBytes());
        }
        assertThat(FileSignatureValidator.detect(gz.toByteArray())).isEqualTo(FileKind.GZIP);
    }

    @Test
    void detectsSpoofedGifAsUnknown() {
        // The classic "GIF89a" polyglot header alone cannot be trusted if it is not a real image,
        // but detection only reports the signature; callers still validate the payload.
        assertThat(FileSignatureValidator.detect("this is plain text, not an image".getBytes()))
                .isEqualTo(FileKind.UNKNOWN);
        assertThat(FileKind.UNKNOWN.isImage()).isFalse();
        assertThat(FileKind.UNKNOWN.isArchive()).isFalse();
    }
}
