package com.nexmemo.web.Controller;

import com.nexmemo.core.entity.User;
import com.nexmemo.security.repository.UserRepository;
import com.nexmemo.web.config.StorageProperties;
import com.nexmemo.web.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final UserRepository userRepository;

    @PostMapping("/signature")
    public ResponseEntity<Map<String, String>> uploadSignature(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        String path = storageProperties.getLocal().getSignatureDir();
        String savedPath = storageService.upload(path, file);
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setSignature(savedPath);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("path", savedPath, "message", "Signature uploaded successfully"));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "dir", defaultValue = "documents") String dir) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        String path = storageProperties.getLocal().getDocumentDir() + "/" + dir;
        String savedPath = storageService.upload(path, file);
        return ResponseEntity.ok(Map.of("path", savedPath, "fileName", file.getOriginalFilename()));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam("path") String filePath) {
        Resource resource = storageService.download(filePath);
        String contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/signature/{userId}")
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
    public ResponseEntity<Map<String, String>> deleteFile(@RequestParam("path") String filePath) {
        boolean deleted = storageService.delete(filePath);
        if (deleted) {
            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "File not found"));
    }
}
