package com.nexmemo.web.service;

import com.nexmemo.core.dto.CompressionResponse;
import com.nexmemo.core.entity.CompressionJob;
import com.nexmemo.core.enums.CompressionPreset;
import com.nexmemo.core.enums.CompressionStatus;
import com.nexmemo.core.enums.CompressionType;
import com.nexmemo.core.repository.CompressionJobRepository;
import com.nexmemo.web.config.CompressionProperties;
import com.nexmemo.web.storage.StorageService;
import com.nexmemo.web.utils.FileCompressionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the asynchronous compressor: submission queues (not runs) work, the worker
 * completes or fails jobs, antivirus rejection is fail-closed, guardrail violations fail the job,
 * and cancel/retry/delete respect ownership and lifecycle.
 */
@ExtendWith(MockitoExtension.class)
class CompressionServiceTest {

    @Mock private CompressionJobRepository jobRepository;
    @Mock private StorageService storageService;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private ClamAvScanner clamAvScanner;
    @Mock private SafeArchiveService safeArchiveService;
    @Mock private TaskExecutor compressionExecutor;

    private CompressionProperties properties;
    private CompressionService service;

    private static final byte[] PAYLOAD = "hello compression world".repeat(20).getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        properties = new CompressionProperties();
        service = new CompressionService(jobRepository, storageService, messagingTemplate,
                properties, clamAvScanner, safeArchiveService, compressionExecutor);
        lenient().when(jobRepository.save(any(CompressionJob.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submitPersistsQueuedJobAndDispatchesWithoutRunningInline() {
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", "text/plain", PAYLOAD);
        when(storageService.uploadRaw(anyString(), any(), anyString()))
                .thenReturn("compression/1/input/key");

        CompressionResponse response = service.submit(file, CompressionType.GZIP, CompressionPreset.BALANCED, 1L);

        assertThat(response.getStatus()).isEqualTo(CompressionStatus.QUEUED);
        assertThat(response.getAttempts()).isEqualTo(1);
        assertThat(response.getPreset()).isEqualTo(CompressionPreset.BALANCED);
        verify(storageService).uploadRaw(eq("compression/1/input"), any(), anyString());
        verify(compressionExecutor).execute(any(Runnable.class));
    }

    @Test
    void submitRejectsFileAboveHardLimit() {
        properties.setMaxInputBytes(10);
        MockMultipartFile file = new MockMultipartFile("file", "big.bin", "application/octet-stream", new byte[100]);

        assertThatThrownBy(() -> service.submit(file, CompressionType.ZIP, CompressionPreset.BALANCED, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum size");
    }

    @Test
    void workerCompletesJobAndStoresArtefactUnderGeneratedKey() {
        CompressionJob job = queuedJob(7L, PAYLOAD);
        when(jobRepository.findById(7L)).thenReturn(Optional.of(job));
        when(storageService.download("in-key"))
                .thenReturn(new ByteArrayResource(PAYLOAD));
        when(storageService.uploadRaw(anyString(), any(), anyString())).thenReturn("out-key");

        service.runJob(7L);

        assertThat(job.getStatus()).isEqualTo(CompressionStatus.COMPLETED);
        assertThat(job.getStorageKey()).isEqualTo("out-key");
        assertThat(job.getCompressedSize()).isPositive();
        assertThat(job.getChecksumCompressed()).isNotNull();
        assertThat(job.getProgressPercent()).isEqualTo(100);
        assertThat(job.getDurationMs()).isNotNull();
        verify(storageService).uploadRaw(eq("compression/1/output"), any(), anyString());
    }

    @Test
    void workerFailsJobWhenScannerReportsMalware() {
        CompressionJob job = queuedJob(8L, PAYLOAD);
        when(jobRepository.findById(8L)).thenReturn(Optional.of(job));
        when(storageService.download("in-key")).thenReturn(new ByteArrayResource(PAYLOAD));
        when(clamAvScanner.isEnabled()).thenReturn(true);
        when(clamAvScanner.scan(any())).thenReturn(false);

        service.runJob(8L);

        assertThat(job.getStatus()).isEqualTo(CompressionStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("Malware");
    }

    @Test
    void workerFailsJobWhenArchiveViolatesGuardrails() {
        byte[] zipLike = new byte[]{0x50, 0x4B, 0x03, 0x04, 0, 0, 0, 0};
        CompressionJob job = queuedJob(9L, zipLike);
        when(jobRepository.findById(9L)).thenReturn(Optional.of(job));
        when(storageService.download("in-key")).thenReturn(new ByteArrayResource(zipLike));
        when(safeArchiveService.inspect(any(), any()))
                .thenThrow(new ArchiveSecurityException("decompression bomb"));

        service.runJob(9L);

        assertThat(job.getStatus()).isEqualTo(CompressionStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("bomb");
    }

    @Test
    void cancellingQueuedJobMarksItCancelled() {
        CompressionJob job = queuedJob(10L, PAYLOAD);
        when(jobRepository.findByIdAndCreatedBy(10L, 1L)).thenReturn(Optional.of(job));

        CompressionResponse response = service.cancel(10L, 1L);

        assertThat(response.getStatus()).isEqualTo(CompressionStatus.CANCELLED);
    }

    @Test
    void retryReQueuesFailedJobAndBumpsAttempts() {
        CompressionJob job = queuedJob(11L, PAYLOAD);
        job.setStatus(CompressionStatus.FAILED);
        job.setAttempts(1);
        when(jobRepository.findByIdAndCreatedBy(11L, 1L)).thenReturn(Optional.of(job));

        CompressionResponse response = service.retry(11L, 1L);

        assertThat(response.getStatus()).isEqualTo(CompressionStatus.QUEUED);
        assertThat(response.getAttempts()).isEqualTo(2);
        verify(compressionExecutor).execute(any(Runnable.class));
    }

    @Test
    void deleteIsRefusedWhileJobIsRunning() {
        CompressionJob job = queuedJob(12L, PAYLOAD);
        job.setStatus(CompressionStatus.IN_PROGRESS);
        when(jobRepository.findByIdAndCreatedBy(12L, 1L)).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> service.deleteJob(12L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cancel the job");
    }

    @Test
    void verifyConfirmsStoredChecksum() throws Exception {
        byte[] gz = FileCompressionUtil.compressGzip(PAYLOAD, "report.txt");
        CompressionJob job = queuedJob(13L, PAYLOAD);
        job.setStatus(CompressionStatus.COMPLETED);
        job.setStorageKey("out-key");
        job.setCompressionType(CompressionType.GZIP);
        job.setChecksumCompressed(FileCompressionUtil.sha256(gz));
        when(jobRepository.findByIdAndCreatedBy(13L, 1L)).thenReturn(Optional.of(job));
        when(storageService.download("out-key")).thenReturn(new ByteArrayResource(gz));

        CompressionResponse response = service.verify(13L, 1L);

        assertThat(response.getVerified()).isTrue();
    }

    @Test
    void imageOptimizeJobCompletesWithoutArchiving() throws Exception {
        byte[] jpeg = sampleJpeg();
        CompressionJob job = queuedJob(14L, jpeg);
        job.setPreset(CompressionPreset.IMAGE_OPTIMIZE);
        job.setOriginalName("photo.jpg");
        when(jobRepository.findById(14L)).thenReturn(Optional.of(job));
        when(storageService.download("in-key")).thenReturn(new ByteArrayResource(jpeg));
        when(storageService.uploadRaw(anyString(), any(), anyString())).thenReturn("out-key");

        service.runJob(14L);

        assertThat(job.getStatus()).isEqualTo(CompressionStatus.COMPLETED);
        assertThat(job.getCompressedName()).isEqualTo("optimized_photo.jpg");
    }

    private CompressionJob queuedJob(Long id, byte[] original) {
        CompressionJob job = new CompressionJob();
        job.setId(id);
        job.setCreatedBy(1L);
        job.setOriginalName("report.txt");
        job.setOriginalSize((long) original.length);
        job.setCompressionType(CompressionType.GZIP);
        job.setPreset(CompressionPreset.BALANCED);
        job.setStatus(CompressionStatus.QUEUED);
        job.setProgressPercent(0);
        job.setAttempts(1);
        job.setInputStorageKey("in-key");
        return job;
    }

    private static byte[] sampleJpeg() throws Exception {
        BufferedImage image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 400, 300);
            g.setColor(new Color(10, 110, 200));
            g.fillOval(40, 40, 320, 220);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
