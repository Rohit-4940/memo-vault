package com.nexmemo.core.enums;

/**
 * Safe, renderable preview family for a stored document (Enterprise Phase 4).
 *
 * <p>Resolved from the file's <b>magic bytes</b>, never from the client-declared content type or
 * extension. Only kinds listed here may be rendered inline; anything else
 * ({@link #NONE}, including HTML and SVG) is download-only, which is what keeps stored content from
 * turning into a stored-XSS vector.</p>
 */
public enum PreviewKind {
    /** Raster image (PNG/JPEG/GIF/BMP/WEBP) — rendered via a same-origin blob URL. */
    IMAGE,
    /** PDF document. */
    PDF,
    /** Plain UTF-8 text (never HTML). */
    TEXT,
    /** Not previewable inline; download only. */
    NONE;

    public boolean isPreviewable() {
        return this != NONE;
    }
}
