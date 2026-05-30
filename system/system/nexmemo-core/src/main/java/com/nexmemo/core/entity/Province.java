package com.nexmemo.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "province")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Province extends BaseEntity<Long> implements Serializable {

    @NotBlank(message = "Province name is required")
    @Column(unique = true, nullable = false)
    @EqualsAndHashCode.Include
    private String name;

    @Column(name = "name_nepali", length = 255)
    private String nameNepali;

    @Column(length = 10)
    private String code;

    public Province(String name, String nameNepali, String code) {
        this.name = name;
        this.nameNepali = nameNepali;
        this.code = code;
    }
}
