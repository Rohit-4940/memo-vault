package com.nexmemo.web.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String upload(String path, MultipartFile file);
    String upload(String path, byte[] bytes, String fileName);

    /**
     * Stores the supplied bytes <b>exactly as provided</b>, bypassing the transparent
     * compression that {@link #upload(String, byte[], String)} may apply. Used by the document
     * vault, where silent gzip/zip rewriting would corrupt content, break the recorded SHA-256
     * and serve the wrong bytes back to the client.
     */
    String uploadRaw(String path, byte[] bytes, String fileName);

    Resource download(String filePath);
    boolean delete(String filePath);
}
