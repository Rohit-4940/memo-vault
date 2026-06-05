package com.nexmemo.core.dto;

import com.nexmemo.core.enums.CompressionType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class CompressionRequest {
    @NotNull
    private CompressionType type = CompressionType.GZIP;
}
