package com.nexmemo.web.storage;

import com.nexmemo.web.config.StorageProperties;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@ConditionalOnProperty(name = "nexmemo.storage.type", havingValue = "minio")
@RequiredArgsConstructor
public class MinioStorageService implements StorageService {

    private final StorageProperties properties;
    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        StorageProperties.MinioProps minio = properties.getMinio();
        minioClient = MinioClient.builder()
                .endpoint(minio.getUrl())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .region(minio.getRegion())
                .build();
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(minio.getBucket()).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder()
                        .bucket(minio.getBucket()).build());
                log.info("Created MinIO bucket: {}", minio.getBucket());
            }
        } catch (Exception e) {
            log.warn("Could not initialize MinIO bucket (may be offline): {}", e.getMessage());
        }
        log.info("MinioStorageService initialized at {}", minio.getUrl());
    }

    @Override
    public String upload(String path, MultipartFile file) {
        try {
            String fileName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
            String objectName = path + "/" + fileName;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    @Override
    public String upload(String path, byte[] bytes, String fileName) {
        try {
            String objectName = path + "/" + fileName;
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(objectName)
                    .stream(new java.io.ByteArrayInputStream(bytes), bytes.length, -1)
                    .build());
            return objectName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload file to MinIO", e);
        }
    }

    @Override
    public Resource download(String filePath) {
        try {
            try (var stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(filePath)
                    .build())) {
                byte[] bytes = stream.readAllBytes();
                return new ByteArrayResource(bytes);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to download file from MinIO: " + filePath, e);
        }
    }

    @Override
    public boolean delete(String filePath) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getMinio().getBucket())
                    .object(filePath)
                    .build());
            return true;
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", filePath, e);
            return false;
        }
    }
}
