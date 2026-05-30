package com.nexmemo.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "district", indexes = {
        @Index(name = "idx_district_name", columnList = "name"),
        @Index(name = "idx_district_province", columnList = "province_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class District extends BaseEntity<Long> implements Serializable {

    @NotBlank(message = "District name is required")
    @Column(nullable = false)
    @EqualsAndHashCode.Include
    private String name;

    @Column(name = "name_nepali", length = 255)
    private String nameNepali;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    @Column(length = 10)
    private String districtCode;

    public District(Province province, String name, String nameNepali) {
        this.province = province;
        this.name = name;
        this.nameNepali = nameNepali;
    }
}
