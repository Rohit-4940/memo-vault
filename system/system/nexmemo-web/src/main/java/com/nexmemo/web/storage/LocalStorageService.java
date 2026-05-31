package com.nexmemo.web.storage;

import com.nexmemo.web.config.StorageProperties;
import com.nexmemo.web.utils.FileCompressionUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
@ConditionalOnProperty(name = "nexmemo.storage.type", havingValue = "local", matchIfMissing = true)
@RequiredArgsConstructor
public class LocalStorageService implements StorageService {

    private final StorageProperties properties;

    @Value("${nexmemo.compression.enabled:true}")
    private boolean compressionEnabled;

    @Value("${nexmemo.compression.threshold-bytes:10240}")
    private int compressionThreshold;

    @Value("${nexmemo.compression.type:gzip}")
    private String compressionType;

    private Path basePath;

    @PostConstruct
    public void init() {
        String os = System.getProperty("os.name").toLowerCase();
        String configuredPath = properties.getLocal().getBasePath();
        if (configuredPath == null || configuredPath.isBlank()) {
            if (os.contains("win")) {
                configuredPath = "C:/nexmemo/uploads";
            } else if (os.contains("mac")) {
                configuredPath = "/Users/Shared/nexmemo/uploads";
            } else {
                configuredPath = "/var/nexmemo/uploads";
            }
        }
        basePath = Paths.get(configuredPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            Files.createDirectories(basePath.resolve(properties.getLocal().getSignatureDir()));
            Files.createDirectories(basePath.resolve(properties.getLocal().getDocumentDir()));
            Files.createDirectories(basePath.resolve(properties.getLocal().getTempDir()));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directories: " + basePath, e);
        }
        log.info("LocalStorageService initialized at {}", basePath);
    }

    @Override
    public String upload(String path, MultipartFile file) {
        long start = System.currentTimeMillis();
        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            String fullPath = path + "/" + fileName;
            Path targetPath = basePath.resolve(fullPath).normalize();
            Files.createDirectories(targetPath.getParent());

            byte[] fileBytes = file.getBytes();

            if (compressionEnabled && FileCompressionUtil.shouldCompress(fileBytes.length, compressionThreshold)) {
                log.debug("Compressing {} ({} bytes) before upload", fileName, fileBytes.length);
                if ("gzip".equalsIgnoreCase(compressionType)) {
                    fileBytes = FileCompressionUtil.compressGzip(fileBytes, fileName);
                    fileName = fileName + ".gz";
                    fullPath = path + "/" + fileName;
                    targetPath = basePath.resolve(fullPath).normalize();
                } else if ("zip".equalsIgnoreCase(compressionType)) {
                    fileBytes = FileCompressionUtil.compressZip(file.getOriginalFilename(), fileBytes);
                    fileName = fileName + ".zip";
                    fullPath = path + "/" + fileName;
                    targetPath = basePath.resolve(fullPath).normalize();
                }
            }

            Files.write(targetPath, fileBytes);
            log.info("Uploaded {} to {} ({} bytes, {}ms)", file.getOriginalFilename(), fullPath, fileBytes.length, System.currentTimeMillis() - start);
            return fullPath;
        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public String upload(String path, byte[] bytes, String fileName) {
        long start = System.currentTimeMillis();
        try {
            String fullPath = path + "/" + fileName;
            Path targetPath = basePath.resolve(fullPath).normalize();
            Files.createDirectories(targetPath.getParent());

            byte[] data = bytes;
            if (compressionEnabled && FileCompressionUtil.shouldCompress(bytes.length, compressionThreshold)) {
                log.debug("Compressing {} ({} bytes)", fileName, bytes.length);
                if ("gzip".equalsIgnoreCase(compressionType)) {
                    data = FileCompressionUtil.compressGzip(bytes, fileName);
                    fileName = fileName + ".gz";
                    fullPath = path + "/" + fileName;
                    targetPath = basePath.resolve(fullPath).normalize();
                } else if ("zip".equalsIgnoreCase(compressionType)) {
                    data = FileCompressionUtil.compressZip(fileName, bytes);
                    fileName = fileName + ".zip";
                    fullPath = path + "/" + fileName;
                    targetPath = basePath.resolve(fullPath).normalize();
                }
            }

            Files.write(targetPath, data);
            log.info("Uploaded {} ({} bytes, {}ms)", fullPath, data.length, System.currentTimeMillis() - start);
            return fullPath;
        } catch (IOException e) {
            log.error("Failed to upload file: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    @Override
    public Resource download(String filePath) {
        Path targetPath = basePath.resolve(filePath).normalize();
        if (!Files.exists(targetPath)) {
            log.warn("File not found for download: {}", filePath);
            throw new RuntimeException("File not found: " + filePath);
        }
        log.debug("Downloading file: {} ({} bytes)", filePath, targetPath.toFile().length());
        return new FileSystemResource(targetPath);
    }

    @Override
    public boolean delete(String filePath) {
        try {
            Path targetPath = basePath.resolve(filePath).normalize();
            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("Deleted file: {}", filePath);
            } else {
                log.warn("File not found for deletion: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
            return false;
        }
    }
}
