package com.nexmemo.web.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "nexmemo.reference")
public class ReferenceProperties {
    private String bankCode = "NMB";
    private int paddingLength = 4;
    private boolean includeBranchCode = true;
    private boolean includeDepartmentCode = false;
    private boolean includeFiscalYear = true;
    private String format = "{bankCode}/{branchCode}/{year}/{sequence}";
}
