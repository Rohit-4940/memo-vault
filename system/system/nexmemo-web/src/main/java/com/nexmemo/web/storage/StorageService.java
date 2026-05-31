package com.nexmemo.web.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface StorageService {
    String upload(String path, MultipartFile file);
    String upload(String path, byte[] bytes, String fileName);
    Resource download(String filePath);
    boolean delete(String filePath);
}
