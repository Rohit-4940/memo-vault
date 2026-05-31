package com.nexmemo.core.entity;

import com.nexmemo.core.enums.Status;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "vendor", indexes = {
    @Index(name = "idx_vendor_code", columnList = "vendor_code", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Vendor extends BaseEntity<Long> implements Serializable {

    @NotBlank
    @Column(name = "vendor_name", nullable = false)
    private String vendorName;

    @NotBlank
    @Column(name = "vendor_code", unique = true, nullable = false)
    private String vendorCode;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(name = "email")
    private String email;

    @Column(name = "phone")
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "pan_number")
    private String panNumber;

    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "payment_terms")
    private String paymentTerms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", insertable = false, updatable = false)
    private User createdByUser;
}
