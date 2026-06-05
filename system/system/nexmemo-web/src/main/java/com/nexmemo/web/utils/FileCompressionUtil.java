package com.nexmemo.web.utils;

import com.nexmemo.core.enums.CompressionType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public final class FileCompressionUtil {

    private FileCompressionUtil() {}

    public static byte[] compress(CompressionType type, byte[] data, String entryName) throws IOException {
        return switch (type) {
            case GZIP -> compressGzip(data, entryName);
            case ZIP -> compressZip(entryName, data);
            case TAR -> compressTar(entryName, data);
            case TAR_GZ -> compressTarGz(entryName, data);
        };
    }

    public static String outputFileName(String originalName, CompressionType type) {
        return switch (type) {
            case GZIP -> originalName + ".gz";
            case ZIP -> originalName + ".zip";
            case TAR -> originalName + ".tar";
            case TAR_GZ -> originalName + ".tar.gz";
        };
    }

    public static byte[] compressGzip(byte[] data, String fileName) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping GZIP compression for empty file: {}", fileName);
            return data;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
            gzos.finish();
            return baos.toByteArray();
        }
    }

    public static byte[] compressZip(String entryName, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping ZIP compression for empty file: {}", entryName);
            return data;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setSize(data.length);
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
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
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(data.length);
            tos.putArchiveEntry(entry);
            tos.write(data);
            tos.closeArchiveEntry();
            return baos.toByteArray();
        }
    }

    public static byte[] compressTarGz(String entryName, byte[] data) throws IOException {
        if (data == null || data.length == 0) {
            log.warn("Skipping TAR.GZ compression for empty file: {}", entryName);
            return data;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(baos);
             TarArchiveOutputStream tos = new TarArchiveOutputStream(gzos)) {
            tos.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);
            TarArchiveEntry entry = new TarArchiveEntry(entryName);
            entry.setSize(data.length);
            tos.putArchiveEntry(entry);
            tos.write(data);
            tos.closeArchiveEntry();
            return baos.toByteArray();
        }
    }

    public static void compressFileToZip(Path sourceFile, Path zipOutput, String entryName) throws IOException {
        byte[] fileData = Files.readAllBytes(sourceFile);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOutput.toFile()))) {
            ZipEntry entry = new ZipEntry(entryName != null ? entryName : sourceFile.getFileName().toString());
            entry.setSize(fileData.length);
            zos.putNextEntry(entry);
            zos.write(fileData);
            zos.closeEntry();
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
