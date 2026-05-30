package com.nexmemo.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "system_config", indexes = {
    @Index(name = "idx_config_key", columnList = "config_key", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SystemConfig extends BaseEntity<Long> implements Serializable {

    @NotBlank(message = "Config key is required")
    @Column(name = "config_key", unique = true, nullable = false)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "is_active")
    private Boolean isActive = true;
}
