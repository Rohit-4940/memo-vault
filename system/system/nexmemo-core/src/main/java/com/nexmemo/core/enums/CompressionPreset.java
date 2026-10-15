package com.nexmemo.core.enums;

/**
 * Compression effort/strategy preset.
 *
 * <p>Presets keep the raw deflate level out of the API surface while still letting the operator
 * trade speed against size. {@link #IMAGE_OPTIMIZE} is a different strategy entirely: instead of
 * wrapping the input in an archive it re-encodes an image in place (see the image optimisation
 * utility), so callers selecting it must upload a real image.</p>
 */
public enum CompressionPreset {
    /** Lowest deflate level — fastest, largest output. */
    FAST(1),
    /** Balanced deflate level (default). */
    BALANCED(6),
    /** Aggressive compression. */
    MAXIMUM_COMPRESSION(9),
    /** Alias for maximum compression for backward compatibility. */
    MAXIMUM(9),
    /** Smallest output - same as max for now, documented as such. */
    SMALLEST_OUTPUT(9),
    /** Auto-select - uses balanced */
    AUTO(6),
    /** Fastest - uses fast */
    FASTEST(1),
    /** Re-encode an image (JPEG quality / PNG recompression) instead of archiving it. */
    IMAGE_OPTIMIZE(6);

    private final int deflateLevel;

    CompressionPreset(int deflateLevel) {
        this.deflateLevel = deflateLevel;
    }

    /** Deflate level (1-9) applied to GZIP/ZIP/TAR_GZ strategies. */
    public int deflateLevel() {
        return deflateLevel;
    }

    public static CompressionPreset from(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return BALANCED;
        }
    }
}
