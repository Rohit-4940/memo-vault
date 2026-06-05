package com.nexmemo.web.service;

import com.nexmemo.core.dto.*;
import com.nexmemo.core.entity.DocumentTransfer;
import com.nexmemo.core.entity.DocumentTransferFile;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.enums.TransferPriority;
import com.nexmemo.core.enums.TransferStatus;
import com.nexmemo.core.repository.DocumentTransferFileRepository;
import com.nexmemo.core.repository.DocumentTransferRepository;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.web.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class DocumentTransferService {

    private final DocumentTransferRepository transferRepository;
    private final DocumentTransferFileRepository fileRepository;
    private final StorageProperties storageProperties;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    public TransferResponse createTransfer(TransferRequest request, List<MultipartFile> files, Long senderId) {
        User sender = userRepository.findById(senderId).orElse(null);
        String senderName = sender != null ? sender.getName() : "User " + senderId;
        String senderBranchName = null;
        Long senderBranchId = null;
        if (sender != null && sender.getBranches() != null && !sender.getBranches().isEmpty()) {
            senderBranchId = sender.getBranches().get(0).getId();
            senderBranchName = sender.getBranches().get(0).getName();
        }

        DocumentTransfer transfer = new DocumentTransfer();
        transfer.setReferenceNo(generateReferenceNo());
        transfer.setSenderId(senderId);
        transfer.setSenderName(senderName);
        transfer.setSenderBranchId(senderBranchId);
        transfer.setSenderBranchName(senderBranchName);
        transfer.setRecipientId(request.getRecipientId());
        transfer.setRecipientName(request.getRecipientName());
        transfer.setRecipientBranchId(request.getRecipientBranchId());
        transfer.setRecipientBranchName(request.getRecipientBranchName());
        transfer.setSubject(request.getSubject());
        transfer.setMessage(request.getMessage());
        transfer.setTransferStatus(TransferStatus.DRAFT);
        transfer.setPriority(request.getPriority() != null ? TransferPriority.valueOf(request.getPriority().toUpperCase()) : TransferPriority.MEDIUM);
        transfer.setTotalFiles(files != null ? files.size() : 0);
        transfer.setCreatedBy(senderId);
        transfer.setCreatedDate(LocalDateTime.now());
        transfer = transferRepository.save(transfer);

        long totalSize = 0;
        if (files != null) {
            for (MultipartFile file : files) {
                DocumentTransferFile tf = new DocumentTransferFile();
                tf.setTransferId(transfer.getId());
                tf.setOriginalName(file.getOriginalFilename());
                tf.setFileSize(file.getSize());
                tf.setMimeType(file.getContentType());
                tf.setStoredPath(storeTransferFile(file, transfer.getId()));
                tf.setCreatedDate(LocalDateTime.now());
                fileRepository.save(tf);
                totalSize += file.getSize();
            }
        }

        transfer.setTotalSize(totalSize);
        transferRepository.save(transfer);

        return toResponse(transfer);
    }

    public TransferResponse sendTransfer(Long transferId, Long userId) {
        DocumentTransfer transfer = transferRepository.findByIdAndSenderId(transferId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (transfer.getTransferStatus() != TransferStatus.DRAFT) {
            throw new IllegalStateException("Only draft transfers can be sent");
        }
        transfer.setTransferStatus(TransferStatus.SENT);
        transfer.setSentDate(LocalDateTime.now());
        transferRepository.save(transfer);

        pushEvent(transfer, "Document transfer sent");
        return toResponse(transfer);
    }

    @Transactional(readOnly = true)
    public TransferHistoryResponse getSentTransfers(Long userId, int page, int size, String status, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DocumentTransfer> transferPage;

        if (search != null && !search.isBlank()) {
            transferPage = transferRepository.findBySenderIdAndSubjectContainingIgnoreCaseOrderByCreatedDateDesc(userId, search, pageable);
        } else if (status != null && !status.isBlank()) {
            try {
                TransferStatus ts = TransferStatus.valueOf(status.toUpperCase());
                transferPage = transferRepository.findBySenderIdAndTransferStatusOrderByCreatedDateDesc(userId, ts, pageable);
            } catch (IllegalArgumentException e) {
                transferPage = transferRepository.findBySenderIdOrderByCreatedDateDesc(userId, pageable);
            }
        } else {
            transferPage = transferRepository.findBySenderIdOrderByCreatedDateDesc(userId, pageable);
        }

        List<TransferResponse> items = transferPage.getContent().stream().map(this::toResponse).toList();
        return TransferHistoryResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(transferPage.getTotalElements())
                .totalPages(transferPage.getTotalPages())
                .first(transferPage.isFirst())
                .last(transferPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public TransferHistoryResponse getReceivedTransfers(Long userId, int page, int size, String status, String search) {
        Pageable pageable = PageRequest.of(page, size);
        Page<DocumentTransfer> transferPage;
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";

        boolean hasSearch = search != null && !search.isBlank();

        if (status != null && !status.isBlank()) {
            try {
                TransferStatus ts = TransferStatus.valueOf(status.toUpperCase());
                transferPage = transferRepository.findReceivedTransfersByStatus(userId, userName, ts, pageable);
            } catch (IllegalArgumentException e) {
                transferPage = transferRepository.findReceivedTransfers(userId, userName, pageable);
            }
        } else {
            transferPage = transferRepository.findReceivedTransfers(userId, userName, pageable);
        }

        List<DocumentTransfer> content = transferPage.getContent();
        if (hasSearch) {
            content = content.stream()
                    .filter(t -> t.getSubject() != null && t.getSubject().toLowerCase().contains(search.toLowerCase()))
                    .toList();
        }

        List<TransferResponse> items = content.stream().map(this::toResponse).toList();

        return TransferHistoryResponse.builder()
                .items(items)
                .page(page)
                .size(size)
                .totalElements(transferPage.getTotalElements())
                .totalPages(transferPage.getTotalPages())
                .first(transferPage.isFirst())
                .last(transferPage.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public TransferDetailResponse getTransferDetail(Long transferId, Long userId) {
        DocumentTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        boolean isRecipient = transfer.getRecipientName() != null &&
                (transfer.getRecipientName().contains(userName) || transfer.getRecipientId().equals(userId));
        if (!transfer.getSenderId().equals(userId) && !isRecipient) {
            throw new IllegalArgumentException("Transfer not found");
        }
        List<DocumentTransferFile> files = fileRepository.findByTransferId(transferId);
        return toDetailResponse(transfer, files);
    }

    public TransferResponse markAsReceived(Long transferId, Long userId) {
        DocumentTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        boolean isRecipient = transfer.getRecipientName() != null &&
                (transfer.getRecipientName().contains(userName) || transfer.getRecipientId().equals(userId));
        if (!isRecipient) {
            throw new IllegalArgumentException("Transfer not found");
        }
        if (transfer.getTransferStatus() != TransferStatus.SENT && transfer.getTransferStatus() != TransferStatus.DELIVERED) {
            throw new IllegalStateException("Transfer must be in SENT or DELIVERED status to receive");
        }
        transfer.setTransferStatus(TransferStatus.RECEIVED);
        transfer.setReceivedDate(LocalDateTime.now());
        transferRepository.save(transfer);

        pushEvent(transfer, "Document transfer received by recipient");
        return toResponse(transfer);
    }

    public TransferResponse markAsDelivered(Long transferId, Long userId) {
        DocumentTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        boolean isRecipient = transfer.getRecipientName() != null &&
                (transfer.getRecipientName().contains(userName) || transfer.getRecipientId().equals(userId));
        if (!isRecipient) {
            throw new IllegalArgumentException("Transfer not found");
        }
        if (transfer.getTransferStatus() != TransferStatus.SENT) {
            throw new IllegalStateException("Transfer must be in SENT status");
        }
        transfer.setTransferStatus(TransferStatus.DELIVERED);
        transferRepository.save(transfer);

        pushEvent(transfer, "Document transfer delivered");
        return toResponse(transfer);
    }

    public TransferResponse archiveTransfer(Long transferId, Long userId) {
        DocumentTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        boolean isRecipient = transfer.getRecipientName() != null &&
                (transfer.getRecipientName().contains(userName) || transfer.getRecipientId().equals(userId));
        if (!transfer.getSenderId().equals(userId) && !isRecipient) {
            throw new IllegalArgumentException("Transfer not found");
        }
        transfer.setTransferStatus(TransferStatus.ARCHIVED);
        transfer.setArchivedDate(LocalDateTime.now());
        transferRepository.save(transfer);
        return toResponse(transfer);
    }

    public void cancelTransfer(Long transferId, Long userId) {
        DocumentTransfer transfer = transferRepository.findByIdAndSenderId(transferId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        if (transfer.getTransferStatus() == TransferStatus.RECEIVED || transfer.getTransferStatus() == TransferStatus.ARCHIVED) {
            throw new IllegalStateException("Cannot cancel a received or archived transfer");
        }
        transfer.setTransferStatus(TransferStatus.CANCELLED);
        transferRepository.save(transfer);

        pushEvent(transfer, "Document transfer cancelled");
    }

    @Transactional(readOnly = true)
    public Path getTransferFilePath(Long fileId, Long userId) {
        DocumentTransferFile tf = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        DocumentTransfer transfer = transferRepository.findById(tf.getTransferId())
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        boolean isRecipient = transfer.getRecipientName() != null &&
                (transfer.getRecipientName().contains(userName) || transfer.getRecipientId().equals(userId));
        if (!transfer.getSenderId().equals(userId) && !isRecipient) {
            throw new IllegalArgumentException("Access denied");
        }
        return Path.of(tf.getStoredPath());
    }

    @Transactional(readOnly = true)
    public String getTransferFileName(Long fileId) {
        DocumentTransferFile tf = fileRepository.findById(fileId)
                .orElseThrow(() -> new IllegalArgumentException("File not found"));
        return tf.getOriginalName();
    }

    @Transactional(readOnly = true)
    public byte[] downloadAllAsZip(Long transferId, Long userId) throws IOException {
        DocumentTransfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found"));
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        boolean isRecipient = transfer.getRecipientName() != null &&
                (transfer.getRecipientName().contains(userName) || transfer.getRecipientId().equals(userId));
        if (!transfer.getSenderId().equals(userId) && !isRecipient) {
            throw new IllegalArgumentException("Access denied");
        }
        List<DocumentTransferFile> files = fileRepository.findByTransferId(transferId);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (DocumentTransferFile tf : files) {
                Path filePath = Path.of(tf.getStoredPath());
                if (Files.exists(filePath)) {
                    ZipEntry entry = new ZipEntry(tf.getOriginalName());
                    zos.putNextEntry(entry);
                    Files.copy(filePath, zos);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    @Transactional(readOnly = true)
    public TransferStats getStats(Long userId) {
        User user = userRepository.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "";
        long totalSent = transferRepository.countBySenderId(userId);
        long totalReceived = transferRepository.countReceivedTransfers(userId, userName);
        long pendingCount = transferRepository.countBySenderIdAndTransferStatus(userId, TransferStatus.DRAFT)
                + transferRepository.countBySenderIdAndTransferStatus(userId, TransferStatus.SENT);
        long completedCount = transferRepository.countBySenderIdAndTransferStatus(userId, TransferStatus.RECEIVED)
                + transferRepository.countBySenderIdAndTransferStatus(userId, TransferStatus.ARCHIVED);
        long totalFiles = transferRepository.totalFilesSentByUser(userId);
        long totalBytes = transferRepository.totalBytesSentByUser(userId);

        return TransferStats.builder()
                .totalSent(totalSent)
                .totalReceived(totalReceived)
                .pendingCount(pendingCount)
                .completedCount(completedCount)
                .totalFilesTransferred(totalFiles)
                .totalBytesTransferred(totalBytes)
                .build();
    }

    private String storeTransferFile(MultipartFile file, Long transferId) {
        try {
            Path transferDir = Path.of(storageProperties.getLocal().getBasePath(), "transfers", String.valueOf(transferId));
            Files.createDirectories(transferDir);
            String name = System.currentTimeMillis() + "_" + (file.getOriginalFilename() != null ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_") : "file");
            Path target = transferDir.resolve(name);
            Files.copy(file.getInputStream(), target);
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to store transfer file", e);
        }
    }

    private String generateReferenceNo() {
        String datePart = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = String.format("%04d", (int) (Math.random() * 10000));
        String refNo = "TRF-" + datePart + "-" + randomPart;
        while (transferRepository.findByReferenceNo(refNo).isPresent()) {
            randomPart = String.format("%04d", (int) (Math.random() * 10000));
            refNo = "TRF-" + datePart + "-" + randomPart;
        }
        return refNo;
    }

    private void pushEvent(DocumentTransfer transfer, String message) {
        try {
            TransferProgressEvent event = TransferProgressEvent.builder()
                    .transferId(transfer.getId())
                    .referenceNo(transfer.getReferenceNo())
                    .subject(transfer.getSubject())
                    .status(transfer.getTransferStatus())
                    .message(message)
                    .senderName(transfer.getSenderName())
                    .recipientName(transfer.getRecipientName())
                    .build();
            messagingTemplate.convertAndSend("/topic/transfer/" + transfer.getRecipientId(), event);
            messagingTemplate.convertAndSend("/topic/transfer/" + transfer.getSenderId(), event);
        } catch (Exception e) {
            log.warn("Failed to push transfer event: {}", e.getMessage());
        }
    }

    private TransferResponse toResponse(DocumentTransfer t) {
        return TransferResponse.builder()
                .id(t.getId())
                .referenceNo(t.getReferenceNo())
                .senderId(t.getSenderId())
                .senderName(t.getSenderName())
                .senderBranchId(t.getSenderBranchId())
                .senderBranchName(t.getSenderBranchName())
                .recipientId(t.getRecipientId())
                .recipientName(t.getRecipientName())
                .recipientBranchId(t.getRecipientBranchId())
                .recipientBranchName(t.getRecipientBranchName())
                .subject(t.getSubject())
                .message(t.getMessage())
                .transferStatus(t.getTransferStatus())
                .priority(t.getPriority())
                .totalFiles(t.getTotalFiles())
                .totalSize(t.getTotalSize())
                .sentDate(t.getSentDate())
                .receivedDate(t.getReceivedDate())
                .createdDate(t.getCreatedDate())
                .build();
    }

    private TransferDetailResponse toDetailResponse(DocumentTransfer t, List<DocumentTransferFile> files) {
        List<TransferDetailResponse.TransferFileItem> fileItems = files.stream()
                .map(f -> TransferDetailResponse.TransferFileItem.builder()
                        .id(f.getId())
                        .originalName(f.getOriginalName())
                        .fileSize(f.getFileSize())
                        .mimeType(f.getMimeType())
                        .build())
                .toList();

        return TransferDetailResponse.builder()
                .id(t.getId())
                .referenceNo(t.getReferenceNo())
                .senderId(t.getSenderId())
                .senderName(t.getSenderName())
                .senderBranchId(t.getSenderBranchId())
                .senderBranchName(t.getSenderBranchName())
                .recipientId(t.getRecipientId())
                .recipientName(t.getRecipientName())
                .recipientBranchId(t.getRecipientBranchId())
                .recipientBranchName(t.getRecipientBranchName())
                .subject(t.getSubject())
                .message(t.getMessage())
                .transferStatus(t.getTransferStatus())
                .priority(t.getPriority())
                .totalFiles(t.getTotalFiles())
                .totalSize(t.getTotalSize())
                .sentDate(t.getSentDate())
                .receivedDate(t.getReceivedDate())
                .archivedDate(t.getArchivedDate())
                .createdDate(t.getCreatedDate())
                .notes(t.getNotes())
                .files(fileItems)
                .build();
    }
}
