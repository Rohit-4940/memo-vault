package com.nexmemo.web.utils;

import com.nexmemo.core.enums.PreviewKind;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PreviewSupport} — the byte-level classification that decides what may render
 * inline. The key security property: HTML/SVG/unknown content is never treated as renderable markup.
 */
class PreviewSupportTest {

    @Test
    void previewKindFor_mapsImagesAndPdf() {
        assertThat(PreviewSupport.previewKindFor(FileKind.PNG)).isEqualTo(PreviewKind.IMAGE);
        assertThat(PreviewSupport.previewKindFor(FileKind.JPEG)).isEqualTo(PreviewKind.IMAGE);
        assertThat(PreviewSupport.previewKindFor(FileKind.WEBP)).isEqualTo(PreviewKind.IMAGE);
        assertThat(PreviewSupport.previewKindFor(FileKind.PDF)).isEqualTo(PreviewKind.PDF);
        assertThat(PreviewSupport.previewKindFor(FileKind.ZIP)).isEqualTo(PreviewKind.NONE);
        assertThat(PreviewSupport.previewKindFor(null)).isEqualTo(PreviewKind.NONE);
    }

    @Test
    void mimeFor_returnsCanonicalTypes() {
        assertThat(PreviewSupport.mimeFor(FileKind.PNG)).isEqualTo("image/png");
        assertThat(PreviewSupport.mimeFor(FileKind.JPEG)).isEqualTo("image/jpeg");
        assertThat(PreviewSupport.mimeFor(FileKind.PDF)).isEqualTo("application/pdf");
        assertThat(PreviewSupport.mimeFor(null)).isEqualTo("application/octet-stream");
    }

    @Test
    void isText_acceptsPlainUtf8() {
        assertThat(PreviewSupport.isText("hello world\nline two".getBytes(StandardCharsets.UTF_8))).isTrue();
    }

    @Test
    void isText_rejectsNulBytes() {
        assertThat(PreviewSupport.isText(new byte[]{'a', 0x00, 'b'})).isFalse();
    }

    @Test
    void isText_rejectsControlCharacters() {
        assertThat(PreviewSupport.isText(new byte[]{'a', 0x07, 'b'})).isFalse();
    }

    @Test
    void isText_rejectsInvalidUtf8() {
        assertThat(PreviewSupport.isText(new byte[]{(byte) 0xC3, (byte) 0x28})).isFalse();
    }

    @Test
    void isText_rejectsEmpty() {
        assertThat(PreviewSupport.isText(new byte[0])).isFalse();
        assertThat(PreviewSupport.isText(null)).isFalse();
    }
}
