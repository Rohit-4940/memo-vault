package com.nexmemo.web.utils;

import com.nexmemo.core.enums.PreviewKind;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Server-side classification of stored bytes for the inline-preview pipeline (Enterprise Phase 4).
 *
 * <p>The stored/declared content type is attacker-controlled and is therefore <b>never</b> trusted.
 * The renderable family is decided from real magic bytes, and plain-text detection additionally
 * rejects anything containing a NUL byte, invalid UTF-8 or C0/C1 control characters — so an uploaded
 * HTML or SVG document can never be served back as renderable markup.</p>
 *
 * @author : NexMemo
 */
public final class PreviewSupport {

    private PreviewSupport() {}

    public static final String TEXT_MIME = "text/plain; charset=UTF-8";

    /** Maps a detected magic-byte kind to a safe preview family. */
    public static PreviewKind previewKindFor(FileKind kind) {
        if (kind == null) {
            return PreviewKind.NONE;
        }
        return switch (kind) {
            case PNG, JPEG, GIF, BMP, WEBP -> PreviewKind.IMAGE;
            case PDF -> PreviewKind.PDF;
            default -> PreviewKind.NONE;
        };
    }

    /** Canonical, safe MIME type to serve for a detected kind (ignores the stored content type). */
    public static String mimeFor(FileKind kind) {
        if (kind == null) {
            return "application/octet-stream";
        }
        return switch (kind) {
            case PNG -> "image/png";
            case JPEG -> "image/jpeg";
            case GIF -> "image/gif";
            case BMP -> "image/bmp";
            case WEBP -> "image/webp";
            case PDF -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    /**
     * True when the sample is plausibly plain UTF-8 text: no NUL byte, decodes cleanly as UTF-8 and
     * contains no control characters other than tab, newline and carriage return.
     */
    public static boolean isText(byte[] sample) {
        if (sample == null || sample.length == 0) {
            return false;
        }
        for (byte b : sample) {
            if (b == 0x00) {
                return false;
            }
        }
        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(sample))
                    .toString();
            for (int i = 0; i < decoded.length(); i++) {
                char c = decoded.charAt(i);
                if (c == '\t' || c == '\n' || c == '\r') {
                    continue;
                }
                if (c < 0x20 || (c >= 0x7F && c <= 0x9F)) {
                    return false;
                }
            }
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
