package com.nexmemo.security.dto;

import com.nexmemo.core.enums.Status;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BranchDto {
    private Long id;

    @NotBlank(message = "Branch name is required")
    private String name;

    @NotBlank(message = "Branch code is required")
    @Pattern(regexp = "^[A-Z0-9]{3,10}$", message = "Branch code must be 3-10 uppercase alphanumeric characters")
    private String branchCode;

    private Status status;

    private Long provinceId;
    private String provinceName;

    private Long districtId;
    private String districtName;

    private Long municipalityVdcId;
    private String municipalityVdcName;

    private String streetName;

    private String wardNumber;

    @Email(message = "Invalid email format")
    private String branchEmail;
}
