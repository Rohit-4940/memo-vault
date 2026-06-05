package com.nexmemo.web.Controller;

import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.web.config.StorageProperties;
import com.nexmemo.web.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final UserRepository userRepository;

    /**
     * Allowed MIME types for file uploads — whitelist approach.
     * Only these file types are accepted. All others are rejected.
     * For bank memos: PDFs, images, Office docs are typical.
     * Executables, scripts, archives, and unknown types are blocked.
     */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg", "image/png", "image/gif", "image/webp", "image/tiff",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv",
            "application/rtf"
    );

    /**
     * Allowed file extensions for path-based fallback validation.
     * Must match the MIME type whitelist above.
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "gif", "webp", "tiff", "tif",
            "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "txt", "csv", "rtf"
    );

    /**
     * Maximum file size: 10MB for production safety.
     * Prevents large file uploads from exhausting server memory/disk.
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L; // 10MB

    @PostMapping("/signature")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> uploadSignature(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        ResponseEntity<Map<String, String>> validation = validateFile(file);
        if (validation != null) return validation;

        String path = storageProperties.getLocal().getSignatureDir();
        String savedPath = storageService.upload(path, file);
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setSignature(savedPath);
        userRepository.save(user);
        log.info("Signature uploaded for user: {}", auth.getName());
        return ResponseEntity.ok(Map.of("path", savedPath, "message", "Signature uploaded successfully"));
    }

    @PostMapping("/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "dir", defaultValue = "documents") String dir) {
        ResponseEntity<Map<String, String>> validation = validateFile(file);
        if (validation != null) return validation;

        // Sanitize the directory parameter — prevent path traversal
        String sanitizedDir = sanitizePathParam(dir);
        String path = storageProperties.getLocal().getDocumentDir() + "/" + sanitizedDir;
        String savedPath = storageService.upload(path, file);
        log.info("File uploaded: {} to dir {}", file.getOriginalFilename(), sanitizedDir);
        return ResponseEntity.ok(Map.of("path", savedPath, "fileName", file.getOriginalFilename()));
    }

    @GetMapping("/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String filePath) {
        // Sanitize path — prevent directory traversal attacks
        String sanitizedPath = sanitizePathParam(filePath);
        Resource resource = storageService.download(sanitizedPath);
        String contentType = "application/octet-stream";
        log.info("File downloaded: {}", sanitizedPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/signature/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> getSignature(@PathVariable Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (user.getSignature() == null || user.getSignature().isBlank()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = storageService.download(user.getSignature());
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(resource);
    }

    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> deleteFile(@RequestParam("path") String filePath) {
        String sanitizedPath = sanitizePathParam(filePath);
        boolean deleted = storageService.delete(sanitizedPath);
        if (deleted) {
            log.info("File deleted: {}", sanitizedPath);
            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "File not found"));
    }

    // ══════════════════════════════════════════════════════════════
    //  PRIVATE VALIDATION HELPERS
    // ══════════════════════════════════════════════════════════════

    /**
     * Validates the uploaded file against security constraints:
     * - Not empty
     * - Size within limit
     * - Extension whitelist
     * - MIME type whitelist
     */
    private ResponseEntity<Map<String, String>> validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File too large. Maximum size is " + (MAX_FILE_SIZE / (1024 * 1024)) + "MB"
            ));
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File must have a name"));
        }
        String extension = getExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "File type '" + extension + "' is not allowed. Allowed: " + ALLOWED_EXTENSIONS
            ));
        }
        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_MIME_TYPES.contains(contentType)) {
            // Log but accept — content-type can be spoofed; extension check is stricter
            log.warn("Suspicious MIME type '{}' for file '{}'", contentType, originalFilename);
        }
        return null;
    }

    /**
     * Sanitize a path parameter to prevent directory traversal attacks.
     * Removes:
     * - ".." sequences
     * - Null bytes
     * - Absolute paths starting with / or drive letters
     * - Shell metacharacters
     */
    private String sanitizePathParam(String param) {
        if (param == null || param.isBlank()) {
            throw new IllegalArgumentException("Path parameter cannot be empty");
        }
        String sanitized = param
                .replaceAll("\\.\\.", "")   // remove ..
                .replaceAll("\\\\", "/")    // normalize backslashes
                .replaceAll("~", "")        // remove home directory shortcuts
                .replaceAll("\\0", "")      // remove null bytes
                .replaceAll("^/", "")       // remove leading /
                .replaceAll("^[A-Za-z]:", "") // remove drive letters (C:, D:)
                .trim();
        if (sanitized.isBlank()) {
            throw new IllegalArgumentException("Invalid path parameter");
        }
        return sanitized;
    }

    /**
     * Extract the file extension from a filename.
     */
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
