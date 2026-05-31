package com.nexmemo.web.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public final class FileCompressionUtil {

    private FileCompressionUtil() {}

    public static byte[] compressGzip(byte[] data, String fileName) {
        if (data == null || data.length == 0) {
            log.warn("Skipping GZIP compression for empty file: {}", fileName);
            return data;
        }
        long start = System.currentTimeMillis();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(data);
            gzos.finish();
            byte[] compressed = baos.toByteArray();
            log.info("GZIP compressed {}: {} bytes -> {} bytes ({}%) in {}ms",
                    fileName, data.length, compressed.length,
                    data.length > 0 ? compressed.length * 100 / data.length : 0,
                    System.currentTimeMillis() - start);
            return compressed;
        } catch (IOException e) {
            log.error("GZIP compression failed for {}: {}", fileName, e.getMessage());
            return data;
        }
    }

    public static byte[] compressZip(String entryName, byte[] data) {
        if (data == null || data.length == 0) {
            log.warn("Skipping ZIP compression for empty file: {}", entryName);
            return data;
        }
        long start = System.currentTimeMillis();
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry(entryName);
            entry.setSize(data.length);
            entry.setTime(System.currentTimeMillis());
            zos.putNextEntry(entry);
            zos.write(data);
            zos.closeEntry();
            byte[] compressed = baos.toByteArray();
            log.info("ZIP compressed {}: {} bytes -> {} bytes ({}%) in {}ms",
                    entryName, data.length, compressed.length,
                    data.length > 0 ? compressed.length * 100 / data.length : 0,
                    System.currentTimeMillis() - start);
            return compressed;
        } catch (IOException e) {
            log.error("ZIP compression failed for {}: {}", entryName, e.getMessage());
            return data;
        }
    }

    public static void compressFileToZip(Path sourceFile, Path zipOutput, String entryName) throws IOException {
        long start = System.currentTimeMillis();
        byte[] fileData = Files.readAllBytes(sourceFile);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOutput.toFile()))) {
            ZipEntry entry = new ZipEntry(entryName != null ? entryName : sourceFile.getFileName().toString());
            entry.setSize(fileData.length);
            zos.putNextEntry(entry);
            zos.write(fileData);
            zos.closeEntry();
        }
        log.info("ZIP file created: {} -> {} ({} bytes in {}ms)",
                sourceFile, zipOutput, Files.size(zipOutput), System.currentTimeMillis() - start);
    }

    public static boolean shouldCompress(long fileSize, int thresholdBytes) {
        return fileSize > thresholdBytes;
    }
}
