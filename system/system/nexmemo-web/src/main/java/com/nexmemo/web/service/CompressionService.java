package com.nexmemo.web.service;

import com.nexmemo.core.dto.CompressionHistoryResponse;
import com.nexmemo.core.dto.CompressionProgressEvent;
import com.nexmemo.core.dto.CompressionResponse;
import com.nexmemo.core.entity.CompressionJob;
import com.nexmemo.core.enums.CompressionStatus;
import com.nexmemo.core.enums.CompressionType;
import com.nexmemo.core.repository.CompressionJobRepository;
import com.nexmemo.web.config.StorageProperties;
import com.nexmemo.web.utils.FileCompressionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CompressionService {

    private final CompressionJobRepository jobRepository;
    private final StorageProperties storageProperties;
    private final SimpMessagingTemplate messagingTemplate;

    public CompressionResponse compressSync(MultipartFile file, CompressionType type, Long userId) throws IOException {
        CompressionJob job = createJob(file, type, userId);
        job.setStartedDate(LocalDateTime.now());
        job.setStatus(CompressionStatus.IN_PROGRESS);
        jobRepository.save(job);

        pushProgress(job, 10, "Reading file...");
        byte[] original = file.getBytes();
        String entryName = file.getOriginalFilename();
        if (entryName == null || entryName.isBlank()) entryName = "file";

        try {
            pushProgress(job, 30, "Compressing...");
            long start = System.currentTimeMillis();
            byte[] compressed = FileCompressionUtil.compress(type, original, entryName);
            long elapsed = System.currentTimeMillis() - start;

            pushProgress(job, 80, "Saving result...");
            String outputName = FileCompressionUtil.outputFileName(entryName, type);
            String storagePath = storeCompressedFile(compressed, outputName, userId);

            job.setCompressedName(outputName);
            job.setCompressedSize((long) compressed.length);
            job.setStoragePath(storagePath);
            job.setStatus(CompressionStatus.COMPLETED);
            job.setCompletedDate(LocalDateTime.now());
            job.setDurationMs(elapsed);
            job.setChecksumOriginal(FileCompressionUtil.sha256(original));
            job.setChecksumCompressed(FileCompressionUtil.sha256(compressed));
            jobRepository.save(job);

            pushProgress(job, 100, "Completed");
            return toResponse(job);

        } catch (Exception e) {
            log.error("Compression failed for {}: {}", entryName, e.getMessage(), e);
            job.setStatus(CompressionStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedDate(LocalDateTime.now());
            jobRepository.save(job);

            pushProgress(job, 100, "Failed: " + e.getMessage());
            return toResponse(job);
        }
    }

    @Async
    public void compressAsync(MultipartFile file, CompressionType type, Long userId) {
        try {
            CompressionResponse resp = compressSync(file, type, userId);
            log.info("Async compression completed for job {}: {}", resp.getId(), resp.getOriginalName());
        } catch (Exception e) {
            log.error("Async compression failed: {}", e.getMessage(), e);
        }
    }

    public CompressionResponse batchCompressSingle(MultipartFile file, CompressionType type, Long userId) {
        try {
            return compressSync(file, type, userId);
        } catch (IOException e) {
            CompressionJob job = new CompressionJob();
            job.setOriginalName(file.getOriginalFilename());
            job.setOriginalSize(file.getSize());
            job.setCompressionType(type);
            job.setStatus(CompressionStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCreatedBy(userId);
            job.setCreatedDate(LocalDateTime.now());
            jobRepository.save(job);
            return toResponse(job);
        }
    }

    public CompressionHistoryResponse getHistory(Long userId, int page, int size, String status, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CompressionJob> jobPage;

        if (search != null && !search.isBlank()) {
            jobPage = jobRepository.findByCreatedByAndOriginalNameContainingIgnoreCaseOrderByCreatedDateDesc(
                    userId, search, pageable);
        } else if (status != null && !status.isBlank()) {
            try {
                CompressionStatus cs = CompressionStatus.valueOf(status.toUpperCase());
                jobPage = jobRepository.findByCreatedByAndStatusOrderByCreatedDateDesc(userId, cs, pageable);
            } catch (IllegalArgumentException e) {
                jobPage = jobRepository.findByCreatedByOrderByCreatedDateDesc(userId, pageable);
            }
        } else {
            jobPage = jobRepository.findByCreatedByOrderByCreatedDateDesc(userId, pageable);
        }

        long totalOrig = jobRepository.totalOriginalBytesByUser(userId);
        long totalComp = jobRepository.totalCompressedBytesByUser(userId);

        List<CompressionResponse> items = jobPage.getContent().stream()
                .map(this::toResponse).toList();

        return CompressionHistoryResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(jobPage.getTotalElements())
                .totalPages(jobPage.getTotalPages())
                .first(jobPage.isFirst())
                .last(jobPage.isLast())
                .totalOriginalBytes(totalOrig)
                .totalCompressedBytes(totalComp)
                .totalSavings(totalOrig - totalComp)
                .build();
    }

    @Transactional
    public void deleteJob(Long jobId, Long userId) {
        CompressionJob job = jobRepository.findByIdAndCreatedBy(jobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Compression job not found"));
        if (job.getStoragePath() != null) {
            try {
                Files.deleteIfExists(Path.of(job.getStoragePath()));
            } catch (IOException e) {
                log.warn("Failed to delete compressed file: {}", job.getStoragePath());
            }
        }
        jobRepository.delete(job);
    }

    public CompressionResponse getJob(Long jobId, Long userId) {
        CompressionJob job = jobRepository.findByIdAndCreatedBy(jobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Compression job not found"));
        return toResponse(job);
    }

    public Path getCompressedFilePath(Long jobId, Long userId) {
        CompressionJob job = jobRepository.findByIdAndCreatedBy(jobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Compression job not found"));
        if (job.getStatus() != CompressionStatus.COMPLETED || job.getStoragePath() == null) {
            throw new IllegalStateException("Compressed file not available");
        }
        return Path.of(job.getStoragePath());
    }

    private CompressionJob createJob(MultipartFile file, CompressionType type, Long userId) {
        CompressionJob job = new CompressionJob();
        job.setOriginalName(file.getOriginalFilename());
        job.setOriginalSize(file.getSize());
        job.setOriginalType(file.getContentType());
        job.setCompressionType(type);
        job.setStatus(CompressionStatus.PENDING);
        job.setCreatedBy(userId);
        job.setCreatedDate(LocalDateTime.now());
        return jobRepository.save(job);
    }

    private String storeCompressedFile(byte[] data, String fileName, Long userId) throws IOException {
        Path userDir = Path.of(storageProperties.getLocal().getBasePath(), "compressed", String.valueOf(userId));
        Files.createDirectories(userDir);
        Path target = userDir.resolve(System.currentTimeMillis() + "_" + fileName);
        Files.write(target, data);
        return target.toAbsolutePath().toString();
    }

    private void pushProgress(CompressionJob job, int percent, String message) {
        try {
            CompressionProgressEvent event = CompressionProgressEvent.builder()
                    .jobId(job.getId())
                    .fileName(job.getOriginalName())
                    .status(job.getStatus())
                    .progressPercent(percent)
                    .message(message)
                    .build();
            messagingTemplate.convertAndSend("/topic/compress/" + job.getCreatedBy(), event);
        } catch (Exception e) {
            log.warn("Failed to push compression progress: {}", e.getMessage());
        }
    }

    private CompressionResponse toResponse(CompressionJob job) {
        int ratio = job.getOriginalSize() != null && job.getOriginalSize() > 0 && job.getCompressedSize() != null
                ? (int) (job.getCompressedSize() * 100 / job.getOriginalSize()) : 0;
        long savings = job.getOriginalSize() != null && job.getCompressedSize() != null
                ? job.getOriginalSize() - job.getCompressedSize() : 0;
        int savingsPercent = job.getOriginalSize() != null && job.getOriginalSize() > 0
                ? (int) (savings * 100 / job.getOriginalSize()) : 0;

        return CompressionResponse.builder()
                .id(job.getId())
                .originalName(job.getOriginalName())
                .originalSize(job.getOriginalSize())
                .originalType(job.getOriginalType())
                .compressedName(job.getCompressedName())
                .compressedSize(job.getCompressedSize())
                .compressionType(job.getCompressionType())
                .status(job.getStatus())
                .durationMs(job.getDurationMs())
                .ratioPercent(ratio)
                .savings(savings)
                .savingsPercent(savingsPercent)
                .errorMessage(job.getErrorMessage())
                .createdDate(job.getCreatedDate())
                .completedDate(job.getCompletedDate())
                .build();
    }
}
