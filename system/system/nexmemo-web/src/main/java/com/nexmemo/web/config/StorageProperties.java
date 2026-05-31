package com.nexmemo.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "nexmemo.storage")
public class StorageProperties {
    private String type = "local";
    private LocalProps local = new LocalProps();
    private MinioProps minio = new MinioProps();

    @Getter @Setter
    public static class LocalProps {
        private String basePath = "C:/nexmemo/uploads";
        private String signatureDir = "signatures";
        private String documentDir = "documents";
        private String tempDir = "temp";
    }

    @Getter @Setter
    public static class MinioProps {
        private String url = "http://localhost:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucket = "nexmemo-docs";
        private String region = "us-east-1";
        private String signatureDir = "signatures";
        private String documentDir = "documents";
    }
}
