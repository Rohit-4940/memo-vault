package com.nexmemo.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "municipality_vdc", indexes = {
        @Index(name = "idx_muni_name", columnList = "name"),
        @Index(name = "idx_muni_district", columnList = "district_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class MunicipalityVdc extends BaseEntity<Long> implements Serializable {

    @NotBlank(message = "Municipality/VDC name is required")
    @Column(nullable = false)
    @EqualsAndHashCode.Include
    private String name;

    @Column(name = "name_nepali", length = 255)
    private String nameNepali;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = false)
    private District district;

    @Column(length = 50)
    private String type;

    public MunicipalityVdc(District district, String name, String nameNepali) {
        this.district = district;
        this.name = name;
        this.nameNepali = nameNepali;
    }
}
