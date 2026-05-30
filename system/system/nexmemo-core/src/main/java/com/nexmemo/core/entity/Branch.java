package com.nexmemo.core.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.nexmemo.core.enums.Status;

import java.io.Serializable;

/**
 * @author : Rohit Chapagain
 * @created : 2026-04-03, Friday
 **/
@Entity
@Table(name = "branch", indexes = {
        @Index(name = "idx_branch_code", columnList = "branch_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Branch extends BaseEntity<Long> implements Serializable {

    @NotBlank(message = "Branch name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "Branch code is required")
    @Column(name = "branch_code", unique = true, nullable = false)
    @EqualsAndHashCode.Include
    private String branchCode;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    private Province province;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private District district;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipality_vdc_id")
    private MunicipalityVdc municipalityVdc;

    private String streetName;

    private String wardNumber;

    private String branchEmail;
}
