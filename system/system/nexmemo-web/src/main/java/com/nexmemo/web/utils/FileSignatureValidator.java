package com.nexmemo.web.utils;

/**
 * Real file-signature (magic byte) detection.
 *
 * <p>The filename and the {@code Content-Type} header supplied by a client are attacker-controlled
 * and must never drive a security decision. This class inspects the leading bytes instead so the
 * compressor can decide, for example, whether an upload really is a GIF before handing it to the
 * image decoder, or really is an archive before walking its entries.</p>
 *
 * @author : NexMemo
 */
public final class FileSignatureValidator {

    private FileSignatureValidator() {}

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] GIF87 = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89 = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] BMP = {'B', 'M'};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] SEVEN_ZIP = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};
    private static final byte[] RAR = {'R', 'a', 'r', '!', 0x1A, 0x07};

    /**
     * Detects the content family from the leading bytes. Returns {@link FileKind#UNKNOWN} for
     * anything not positively identified — callers must treat that as "do not trust".
     */
    public static FileKind detect(byte[] data) {
        if (data == null || data.length < 4) {
            return FileKind.UNKNOWN;
        }
        if (startsWith(data, PNG)) return FileKind.PNG;
        if (startsWith(data, JPEG)) return FileKind.JPEG;
        if (startsWith(data, GIF87) || startsWith(data, GIF89)) return FileKind.GIF;
        if (startsWith(data, BMP)) return FileKind.BMP;
        if (startsWith(data, SEVEN_ZIP)) return FileKind.SEVEN_ZIP;
        if (startsWith(data, RAR)) return FileKind.RAR;
        if (startsWith(data, PDF)) return FileKind.PDF;
        if (startsWith(data, RIFF) && data.length >= 12 && regionEquals(data, 8, WEBP)) return FileKind.WEBP;
        if (data.length >= 4 && data[0] == 0x50 && data[1] == 0x4B
                && (data[2] == 0x03 || data[2] == 0x05 || data[2] == 0x07)
                && (data[3] == 0x04 || data[3] == 0x06 || data[3] == 0x08)) {
            return FileKind.ZIP;
        }
        if (data.length >= 2 && data[0] == 0x1F && (data[1] & 0xFF) == 0x8B) {
            return FileKind.GZIP;
        }
        // POSIX tar stores the literal "ustar" magic at offset 257.
        if (data.length >= 262 && regionEquals(data, 257, new byte[]{'u', 's', 't', 'a', 'r'})) {
            return FileKind.TAR;
        }
        return FileKind.UNKNOWN;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean regionEquals(byte[] data, int offset, byte[] expected) {
        if (offset < 0 || data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
