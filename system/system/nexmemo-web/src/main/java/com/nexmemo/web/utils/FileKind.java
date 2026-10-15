package com.nexmemo.web.utils;

/**
 * Broad content families recognised by real magic-byte inspection (see
 * {@link FileSignatureValidator}). The client-declared content type / extension is never trusted.
 */
public enum FileKind {
    ZIP,
    GZIP,
    TAR,
    SEVEN_ZIP,
    RAR,
    PNG,
    JPEG,
    GIF,
    BMP,
    WEBP,
    PDF,
    UNKNOWN;

    /** True for container formats the guardrail inspector knows how to walk. */
    public boolean isArchive() {
        return this == ZIP || this == GZIP || this == TAR;
    }

    /** True for raster images the image optimiser can re-encode. */
    public boolean isImage() {
        return this == PNG || this == JPEG || this == GIF || this == BMP;
    }
}
