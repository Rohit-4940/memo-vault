package com.nexmemo.web.service;

import com.nexmemo.core.dto.CompressionHistoryResponse;
import com.nexmemo.core.dto.CompressionProgressEvent;
import com.nexmemo.core.dto.CompressionResponse;
import com.nexmemo.core.entity.CompressionJob;
import com.nexmemo.core.enums.CompressionPreset;
import com.nexmemo.core.enums.CompressionStatus;
import com.nexmemo.core.enums.CompressionType;
import com.nexmemo.core.repository.CompressionJobRepository;
import com.nexmemo.web.config.AsyncConfig;
import com.nexmemo.web.config.CompressionProperties;
import com.nexmemo.web.storage.StorageService;
import com.nexmemo.web.utils.FileCompressionUtil;
import com.nexmemo.web.utils.FileKind;
import com.nexmemo.web.utils.FileSignatureValidator;
import com.nexmemo.web.utils.ImageCompressionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * Asynchronous, guardrailed compression service (Enterprise Phase 3).
 *
 * <p>Compression never runs on an HTTP request thread. A submission validates and persists the
 * input into private storage, records a {@code QUEUED} job and hands the job id to a bounded worker
 * pool. Workers emit progress on the owner's private STOMP queue
 * ({@code /user/queue/compress}), scan inputs for malware, inspect archives against bomb and
 * traversal guardrails, and write artefacts under generated storage keys.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
public class CompressionService {

    /** Retries are capped to bound resource use from a pathological input. */
    private static final int MAX_ATTEMPTS = 3;

    private final CompressionJobRepository jobRepository;
    private final StorageService storageService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CompressionProperties properties;
    private final ClamAvScanner clamAvScanner;
    private final SafeArchiveService safeArchiveService;
    private final TaskExecutor compressionExecutor;

    public CompressionService(CompressionJobRepository jobRepository,
                              StorageService storageService,
                              SimpMessagingTemplate messagingTemplate,
                              CompressionProperties properties,
                              ClamAvScanner clamAvScanner,
                              SafeArchiveService safeArchiveService,
                              @Qualifier(AsyncConfig.COMPRESSION_EXECUTOR) TaskExecutor compressionExecutor) {
        this.jobRepository = jobRepository;
        this.storageService = storageService;
        this.messagingTemplate = messagingTemplate;
        this.properties = properties;
        this.clamAvScanner = clamAvScanner;
        this.safeArchiveService = safeArchiveService;
        this.compressionExecutor = compressionExecutor;
    }

    // ─── Submission ─────────────────────────────────────────────────────────

    public CompressionResponse submit(MultipartFile file, CompressionType type, CompressionPreset preset, Long userId) {
        byte[] original = readUpload(file);
        CompressionJob job = persistQueuedJob(file, type, preset, userId, original, null);
        dispatch(job.getId());
        return toResponse(job);
    }

    public List<CompressionResponse> submitBatch(List<MultipartFile> files, CompressionType type, CompressionPreset preset, Long userId) {
        String batchId = UUID.randomUUID().toString();
        List<CompressionResponse> responses = new ArrayList<>();
        for (MultipartFile file : files) {
            try {
                byte[] original = readUpload(file);
                CompressionJob job = persistQueuedJob(file, type, preset, userId, original, batchId);
                dispatch(job.getId());
                responses.add(toResponse(job));
            } catch (RuntimeException e) {
                // One bad file must not sink the whole batch — record it as a failed job.
                CompressionJob failed = new CompressionJob();
                failed.setOriginalName(safeName(file.getOriginalFilename()));
                failed.setOriginalSize(file.getSize());
                failed.setCompressionType(type);
                failed.setPreset(preset);
                failed.setStatus(CompressionStatus.FAILED);
                failed.setErrorMessage(e.getMessage());
                failed.setBatchId(batchId);
                failed.setCreatedBy(userId);
                failed.setCreatedDate(LocalDateTime.now());
                jobRepository.save(failed);
                responses.add(toResponse(failed));
            }
        }
        return responses;
    }

    private CompressionJob persistQueuedJob(MultipartFile file, CompressionType type, CompressionPreset preset,
                                            Long userId, byte[] original, String batchId) {
        String displayName = safeName(file.getOriginalFilename());
        String inputKey = storageService.uploadRaw(
                "compression/" + userId + "/input", original, UUID.randomUUID() + "_" + displayName);

        CompressionJob job = new CompressionJob();
        job.setOriginalName(displayName);
        job.setOriginalSize((long) original.length);
        job.setOriginalType(file.getContentType());
        job.setCompressionType(type);
        job.setPreset(preset);
        job.setStatus(CompressionStatus.QUEUED);
        job.setProgressPercent(0);
        job.setAttempts(1);
        job.setBatchId(batchId);
        job.setInputStorageKey(inputKey);
        job.setChecksumOriginal(FileCompressionUtil.sha256(original));
        job.setCreatedBy(userId);
        job.setCreatedDate(LocalDateTime.now());
        return jobRepository.save(job);
    }

    private void dispatch(Long jobId) {
        try {
            compressionExecutor.execute(() -> runJob(jobId));
        } catch (RejectedExecutionException e) {
            // Pool saturated: record the rejection instead of blocking a request thread.
            log.warn("Compression executor rejected job {}: {}", jobId, e.getMessage());
            jobRepository.findById(jobId).ifPresent(job -> {
                try { fail(job, "Server is busy, please retry shortly"); } catch (Exception ignored) { }
            });
            throw new IllegalStateException("Compression queue is full; please retry shortly");
        }
    }

    // ─── Worker ─────────────────────────────────────────────────────────────

    @org.springframework.transaction.annotation.Transactional
    void runJob(Long jobId) {
        CompressionJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            return;
        }
        if (job.getStatus() == CompressionStatus.CANCELLED || job.isCancelRequested()) {
            job = markCancelled(job);
            return;
        }

        job.setStatus(CompressionStatus.IN_PROGRESS);
        job.setStartedDate(LocalDateTime.now());
        long start = System.currentTimeMillis();
        String outputKey = null;
        try {
            log.info("Job {}: starting processing", jobId);
            job = progress(job, 5, "Starting");
            log.info("Job {}: loading input", jobId);
            byte[] original = loadInput(job);
            log.info("Job {}: loaded input {} bytes", jobId, original != null ? original.length : 0);
            if (cancelled(job)) {
                job = markCancelled(job);
                return;
            }

            log.info("Job {}: detecting file kind", jobId);
            FileKind kind = FileSignatureValidator.detect(original);
            log.info("Job {}: detected kind {}", jobId, kind);

            job = progress(job, 20, "Scanning for malware");
            log.info("Job {}: scanning for malware", jobId);
            scanForMalware(original);
            log.info("Job {}: malware scan done", jobId);

            job = progress(job, 40, "Validating archive");
            log.info("Job {}: inspecting if archive", jobId);
            inspectIfArchive(job, original, kind);
            log.info("Job {}: inspection done", jobId);

            if (cancelled(job)) {
                job = markCancelled(job);
                return;
            }

            job = progress(job, 60, "Compressing");
            log.info("Job {}: producing compressed result", jobId);
            byte[] result = produce(job, original, kind);
            log.info("Job {}: produced result {} bytes", jobId, result != null ? result.length : 0);

            job = progress(job, 85, "Storing result");
            String resultName = outputName(job, kind);
            outputKey = storageService.uploadRaw(
                    "compression/" + job.getCreatedBy() + "/output", result, UUID.randomUUID() + "_" + resultName);

            if (!isReadable(job, result)) {
                throw new IllegalStateException("Produced artefact failed integrity verification");
            }

            job.setCompressedName(resultName);
            job.setCompressedSize((long) result.length);
            job.setStorageKey(outputKey);
            job.setChecksumCompressed(FileCompressionUtil.sha256(result));
            job.setDurationMs(System.currentTimeMillis() - start);
            job.setStatus(CompressionStatus.COMPLETED);
            job.setCompletedDate(LocalDateTime.now());
            job.setProgressPercent(100);
            job = jobRepository.save(job);
            sendEvent(job, 100, "Completed");
            log.info("Compression job {} completed in {}ms ({} -> {} bytes)",
                    job.getId(), job.getDurationMs(), job.getOriginalSize(), job.getCompressedSize());
        } catch (ArchiveSecurityException e) {
            log.warn("Compression job {} rejected by guardrails: {}", jobId, e.getMessage());
            job = fail(job, e.getMessage());
        } catch (Exception e) {
            log.error("Compression job {} failed: {}", jobId, e.getMessage(), e);
            job = fail(job, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private byte[] produce(CompressionJob job, byte[] original, FileKind kind) throws IOException {
        if (job.getPreset() == CompressionPreset.IMAGE_OPTIMIZE) {
            if (!kind.isImage()) {
                throw new IllegalArgumentException("IMAGE_OPTIMIZE requires a recognised image upload");
            }
            return ImageCompressionUtil.optimize(original, 0.82f);
        }
        int level = job.getPreset() != null ? job.getPreset().deflateLevel() : FileCompressionUtil.DEFAULT_LEVEL;
        return FileCompressionUtil.compress(job.getCompressionType(), original, job.getOriginalName(), level);
    }

    private String outputName(CompressionJob job, FileKind kind) {
        if (job.getPreset() == CompressionPreset.IMAGE_OPTIMIZE) {
            return "optimized_" + job.getOriginalName();
        }
        return FileCompressionUtil.outputFileName(job.getOriginalName(), job.getCompressionType());
    }

    /**
     * Post-condition check on a produced artefact. Archived output is re-opened and drained; an
     * optimised image is decoded back. Either way a corrupt result is never advertised as valid.
     */
    private boolean isReadable(CompressionJob job, byte[] bytes) {
        if (job.getPreset() == CompressionPreset.IMAGE_OPTIMIZE) {
            try {
                return ImageIO.read(new ByteArrayInputStream(bytes)) != null;
            } catch (IOException e) {
                return false;
            }
        }
        return FileCompressionUtil.verifyReadable(job.getCompressionType(), bytes);
    }

    private void scanForMalware(byte[] content) {        if (!properties.getGuardrails().isScanWithClamAv() || !clamAvScanner.isEnabled()) {
            return;
        }
        boolean clean = clamAvScanner.scan(content);
        if (!clean) {
            throw new ArchiveSecurityException("Malware detected by antivirus scanner");
        }
    }

    private void inspectIfArchive(CompressionJob job, byte[] content, FileKind kind) {
        if (!properties.getGuardrails().isInspectArchives() || !kind.isArchive()) {
            return;
        }
        SafeArchiveService.Result result = safeArchiveService.inspect(content, kind);
        job.setArchiveEntryCount(result.entryCount());
        job.setArchiveUncompressedBytes(result.uncompressedBytes());
    }

    private byte[] loadInput(CompressionJob job) throws IOException {
        if (job.getInputStorageKey() == null) {
            throw new IllegalStateException("Job has no stored input to process");
        }
        Resource resource = storageService.download(job.getInputStorageKey());
        try (InputStream in = resource.getInputStream()) {
            long size = job.getOriginalSize() != null ? job.getOriginalSize() : resource.contentLength();
            if (size > 50L * 1024 * 1024) {
                throw new IllegalStateException("Input too large to load in memory; use streaming path");
            }
            return in.readAllBytes();
        }
    }

    // ─── Cancel / retry / verify ────────────────────────────────────────────

    @Transactional
    public CompressionResponse cancel(Long jobId, Long userId) {
        CompressionJob job = requireJob(jobId, userId);
        if (job.getStatus() == CompressionStatus.QUEUED) {
            job = markCancelled(job);
        } else if (job.getStatus() == CompressionStatus.IN_PROGRESS) {
            job.setCancelRequested(true);
            job = jobRepository.save(job);
        } else {
            throw new IllegalStateException("Only queued or running jobs can be cancelled");
        }
        return toResponse(job);
    }

    @Transactional
    public CompressionResponse retry(Long jobId, Long userId) {
        CompressionJob job = requireJob(jobId, userId);
        if (job.getStatus() != CompressionStatus.FAILED && job.getStatus() != CompressionStatus.CANCELLED) {
            throw new IllegalStateException("Only failed or cancelled jobs can be retried");
        }
        int attempts = job.getAttempts() == null ? 0 : job.getAttempts();
        if (attempts >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Retry limit reached for this job");
        }
        job.setAttempts(attempts + 1);
        job.setStatus(CompressionStatus.QUEUED);
        job.setCancelRequested(false);
        job.setErrorMessage(null);
        job.setProgressPercent(0);
        job.setStartedDate(null);
        job.setCompletedDate(null);
        job.setDurationMs(null);
        job = jobRepository.save(job);
        dispatch(job.getId());
        return toResponse(job);
    }

    @Transactional
    public CompressionResponse verify(Long jobId, Long userId) {
        CompressionJob job = requireJob(jobId, userId);
        if (job.getStatus() != CompressionStatus.COMPLETED || job.getStorageKey() == null) {
            throw new IllegalStateException("Only completed jobs can be verified");
        }
        boolean ok = false;
        try {
            Resource resource = storageService.download(job.getStorageKey());
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readAllBytes();
            }
            String checksum = FileCompressionUtil.sha256(bytes);
            ok = checksum != null
                    && checksum.equals(job.getChecksumCompressed())
                    && isReadable(job, bytes);
        } catch (Exception e) {
            log.warn("Verification of job {} failed: {}", jobId, e.getMessage());
        }
        job.setVerified(ok);
        job = jobRepository.save(job);
        return toResponse(job);
    }

    // ─── Queries ────────────────────────────────────────────────────────────

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

        List<CompressionResponse> items = jobPage.getContent().stream().map(this::toResponse).toList();

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

    public CompressionResponse getJob(Long jobId, Long userId) {
        return toResponse(requireJob(jobId, userId));
    }

    public List<CompressionResponse> getBatch(String batchId, Long userId) {
        return jobRepository.findByCreatedByAndBatchIdOrderByCreatedDateAsc(userId, batchId)
                .stream().map(this::toResponse).toList();
    }

    public Resource getCompressedResource(Long jobId, Long userId) {
        CompressionJob job = requireJob(jobId, userId);
        if (job.getStatus() != CompressionStatus.COMPLETED) {
            throw new IllegalStateException("Compressed file not available");
        }
        if (job.getStorageKey() != null) {
            return storageService.download(job.getStorageKey());
        }
        // Legacy rows created before Phase 3 stored an absolute path.
        if (job.getStoragePath() != null) {
            Resource legacy = new org.springframework.core.io.FileSystemResource(job.getStoragePath());
            if (legacy.exists()) {
                return legacy;
            }
        }
        throw new IllegalStateException("Compressed file not available");
    }

    @Transactional
    public void deleteJob(Long jobId, Long userId) {
        CompressionJob job = requireJob(jobId, userId);
        // A running job must be stopped before its bytes are removed.
        if (job.getStatus() == CompressionStatus.IN_PROGRESS || job.getStatus() == CompressionStatus.QUEUED) {
            throw new IllegalStateException("Cancel the job before deleting it");
        }
        deleteBlob(job.getInputStorageKey());
        deleteBlob(job.getStorageKey());
        if (job.getStoragePath() != null) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(job.getStoragePath()));
            } catch (IOException e) {
                log.warn("Failed to delete legacy compressed file: {}", job.getStoragePath());
            }
        }
        jobRepository.delete(job);
    }

    /**
     * On startup no worker is running, so any job still marked {@code IN_PROGRESS} was interrupted
     * by a restart. Re-queue it rather than leaving it stuck forever.
     */
    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void recoverInterruptedJobs() {
        List<CompressionJob> stuck = jobRepository.findByStatus(CompressionStatus.IN_PROGRESS);
        for (CompressionJob job : stuck) {
            log.warn("Re-queueing compression job {} interrupted by restart", job.getId());
            job.setStatus(CompressionStatus.QUEUED);
            job.setCancelRequested(false);
            job.setProgressPercent(0);
            job.setErrorMessage(null);
            try {
                job = jobRepository.save(job);
                dispatch(job.getId());
            } catch (Exception e) {
                log.warn("Could not re-queue job {}: {}", job.getId(), e.getMessage());
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private CompressionJob requireJob(Long jobId, Long userId) {
        return jobRepository.findByIdAndCreatedBy(jobId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Compression job not found"));
    }

    private byte[] readUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > properties.getMaxInputBytes()) {
            throw new IllegalArgumentException("File exceeds the maximum size of "
                    + (properties.getMaxInputBytes() / (1024 * 1024)) + "MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the uploaded file", e);
        }
    }

    private boolean cancelled(CompressionJob job) {
        CompressionJob fresh = jobRepository.findById(job.getId()).orElse(job);
        return fresh.isCancelRequested() || fresh.getStatus() == CompressionStatus.CANCELLED;
    }

    private CompressionJob markCancelled(CompressionJob job) {
        job.setStatus(CompressionStatus.CANCELLED);
        job.setCancelRequested(false);
        job.setCompletedDate(LocalDateTime.now());
        job = jobRepository.save(job);
        sendEvent(job, job.getProgressPercent() == null ? 0 : job.getProgressPercent(), "Cancelled");
        return job;
    }

    private CompressionJob fail(CompressionJob job, String message) {
        job.setStatus(CompressionStatus.FAILED);
        job.setErrorMessage(truncate(message));
        job.setCompletedDate(LocalDateTime.now());
        if (job.getProgressPercent() == null) {
            job.setProgressPercent(0);
        }
        job = jobRepository.save(job);
        sendEvent(job, job.getProgressPercent(), "Failed: " + message);
        return job;
    }

    private void deleteBlob(String key) {
        if (key == null) {
            return;
        }
        try {
            storageService.delete(key);
        } catch (Exception e) {
            log.warn("Failed to delete stored blob {}: {}", key, e.getMessage());
        }
    }

    private CompressionJob progress(CompressionJob job, int percent, String message) {
        Integer current = job.getProgressPercent();
        if (current != null && percent <= current && percent < 95) {
            return job;
        }
        job.setProgressPercent(percent);
        try {
            job = jobRepository.save(job);
        } catch (Exception e) {
            log.warn("Failed to save progress for job {}: {}", job.getId(), e.getMessage());
        }
        sendEvent(job, percent, message);
        return job;
    }

    private void sendEvent(CompressionJob job, int percent, String message) {
        try {
            CompressionProgressEvent event = CompressionProgressEvent.builder()
                    .jobId(job.getId())
                    .fileName(job.getOriginalName())
                    .status(job.getStatus())
                    .progressPercent(percent)
                    .message(message)
                    .build();
            // Private, per-user destination: the broker scopes /user/** to the authenticated principal.
            messagingTemplate.convertAndSendToUser(String.valueOf(job.getCreatedBy()), "/queue/compress", event);
        } catch (Exception e) {
            log.warn("Failed to push compression progress: {}", e.getMessage());
        }
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank()) {
            return "file";
        }
        return cleaned.length() > 180 ? cleaned.substring(cleaned.length() - 180) : cleaned;
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 1900 ? message.substring(0, 1900) : message;
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
                .preset(job.getPreset())
                .status(job.getStatus())
                .progressPercent(job.getProgressPercent())
                .attempts(job.getAttempts())
                .batchId(job.getBatchId())
                .verified(job.getVerified())
                .archiveEntryCount(job.getArchiveEntryCount())
                .archiveUncompressedBytes(job.getArchiveUncompressedBytes())
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
