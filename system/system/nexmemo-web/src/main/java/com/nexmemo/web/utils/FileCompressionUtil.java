package com.nexmemo.web.utils;

import com.nexmemo.core.enums.CompressionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipParameters;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/**
 * Low-level archive helpers used by the compressor and by the transparent storage layer.
 *
 * <p>Phase 3 hardened this class: every entry name written into an archive is sanitised against
 * zip-slip (no leading separators, no {@code ..}, no control characters) and callers may pick a
 * deflate level. {@link #verifyReadable} re-opens a produced archive and drains it so a truncated
 * or corrupt artefact is detected before it is advertised as a valid download.</p>
 */
@Slf4j
public final class FileCompressionUtil {

    /** Balanced deflate level used when the caller does not specify one. */
    public static final int DEFAULT_LEVEL = 6;

    private FileCompressionUtil() {}

    public static byte[] compress(CompressionType type, byte[] data, String entryName) throws IOException {
        return compress(type, data, entryName, DEFAULT_LEVEL);
    }

    public static byte[] compress(CompressionType type, byte[] data, String entryName, int level) throws IOException {
        int safeLevel = Math.min(9, Math.max(1, level));
        String safeEntry = sanitizeEntryName(entryName);
        return switch (type) {
            case GZIP -> compressGzip(data, safeEntry, safeLevel);
            case ZIP -> compressZip(safeEntry, data, safeLevel);
            case TAR -> compressTar(safeEntry, data);
            case TAR_GZ -> compressTarGz(safeEntry, data, safeLevel);
        };
    }

    public static String outputFileName(String originalName, CompressionType type) {
        String safe = sanitizeEntryName(originalName);
        return switch (type) {
            case GZIP -> safe + ".gz";
            case ZIP -> safe + ".zip";
            case TAR -> safe + ".tar";
            case TAR_GZ -> safe + ".tar.gz";
        };
    }

    /**
     * Strips anything that could turn an archive entry into a path-traversal payload for whoever
     * extracts it later: leading separators, {@code ..} segments, drive letters and control bytes.
     * Falls back to {@code "file"} when nothing usable remains.
     */
    public static String sanitizeEntryName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        // Normalise both separator styles, then keep only the last segment (no directories).
        String normalised = name.replace('\\', '/');
        int slash = normalised.lastIndexOf('/');
        if (slash >= 0) {
            normalised = normalised.substring(slash + 1);
        }
        normalised = normalised.replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalised.isBlank() || ".".equals(normalised) || "..".equals(normalised)) {
            return "file";
        }
        return normalised.length() > 200 ? normalised.substring(normalised.length() - 200) : normalised;
    }

    public static byte[] compressGzip(byte[] data, String fileName) throws IOException {
        return compressGzip(data, fileName, DEFAULT_LEVEL);
    }

    public static byte[] compressGzip(byte[] data, String fileName, int level) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping GZIP compression for empty file: {}", fileName);
            return data;
        }
        GzipParameters params = new GzipParameters();
        params.setCompressionLevel(Math.min(9, Math.max(1, level)));
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(baos, params)) {
            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();
        }
    }

    public static byte[] compressZip(String entryName, byte[] data) throws IOException {
        return compressZip(entryName, data, DEFAULT_LEVEL);
    }

    public static byte[] compressZip(String entryName, byte[] data, int level) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping ZIP compression for empty file: {}", entryName);
            return data;
        }
        String safeEntry = sanitizeEntryName(entryName);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos)) {
            zos.setLevel(Math.min(9, Math.max(1, level)));
            ZipArchiveEntry entry = new ZipArchiveEntry(safeEntry);
            entry.setSize(data.length);
            zos.putArchiveEntry(entry);
            zos.write(data);
            zos.closeArchiveEntry();
            zos.finish();
            return baos.toByteArray();
        }
    }

    public static byte[] compressTar(String entryName, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping TAR compression for empty file: {}", entryName);
            return data;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             TarArchiveOutputStream tos = new TarArchiveOutputStream(baos)) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry(sanitizeEntryName(entryName));
            entry.setSize(data.length);
            tos.putArchiveEntry(entry);
            tos.write(data);
            tos.closeArchiveEntry();
            tos.finish();
            return baos.toByteArray();
        }
    }

    public static byte[] compressTarGz(String entryName, byte[] data) throws IOException {
        return compressTarGz(entryName, data, DEFAULT_LEVEL);
    }

    public static byte[] compressTarGz(String entryName, byte[] data, int level) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping TAR.GZ compression for empty file: {}", entryName);
            return data;
        }
        GzipParameters params = new GzipParameters();
        params.setCompressionLevel(Math.min(9, Math.max(1, level)));
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(baos, params);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gzos)) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry(sanitizeEntryName(entryName));
            entry.setSize(data.length);
            tos.putArchiveEntry(entry);
            tos.write(data);
            tos.closeArchiveEntry();
            tos.finish();
            gzos.finish();
            return baos.toByteArray();
        }
    }

    public static void compressFileToZip(Path sourceFile, Path zipOutput, String entryName) throws IOException {
        byte[] fileData = Files.readAllBytes(sourceFile);
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(zipOutput.toFile())) {
            zos.setLevel(DEFAULT_LEVEL);
            ZipArchiveEntry entry = new ZipArchiveEntry(sanitizeEntryName(
                    entryName != null ? entryName : sourceFile.getFileName().toString()));
            entry.setSize(fileData.length);
            zos.putArchiveEntry(entry);
            zos.write(fileData);
            zos.closeArchiveEntry();
            zos.finish();
        }
    }

    /**
     * Re-opens an archive and drains every stream, so structural corruption or truncation surfaces
     * as {@code false} rather than as a broken download later. Never assumes the bytes are safe:
     * callers must have already validated the input.
     */
    public static boolean verifyReadable(CompressionType type, byte[] data) {
        if (data == null) {
            return false;
        }
        try {
            return switch (type) {
                case GZIP -> drain(new GZIPInputStream(new ByteArrayInputStream(data)));
                case ZIP -> drainZip(data);
                case TAR -> drainTar(new ByteArrayInputStream(data));
                case TAR_GZ -> drainTar(new GzipCompressorInputStream(new ByteArrayInputStream(data)));
            };
        } catch (Exception e) {
            log.warn("Archive failed integrity re-read: {}", e.getMessage());
            return false;
        }
    }

    private static boolean drain(InputStream in) throws IOException {
        try (InputStream stream = in) {
            byte[] buffer = new byte[8192];
            while (stream.read(buffer) != -1) {
                // draining forces full decompression / CRC verification
            }
            return true;
        }
    }

    private static boolean drainZip(byte[] data) throws IOException {
        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new ByteArrayInputStream(data))) {
            byte[] buffer = new byte[8192];
            while (zis.getNextEntry() != null) {
                while (zis.read(buffer) != -1) {
                    // drain entry
                }
            }
            return true;
        }
    }

    private static boolean drainTar(InputStream in) throws IOException {
        try (TarArchiveInputStream tis = new TarArchiveInputStream(in)) {
            byte[] buffer = new byte[8192];
            TarArchiveEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                while (tis.read(buffer) != -1) {
                    // drain entry
                }
            }
            return true;
        }
    }

    public static boolean shouldCompress(long fileSize, int thresholdBytes) {
        return fileSize > thresholdBytes;
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            log.warn("SHA-256 not available: {}", e.getMessage());
            return null;
        }
    }
}
