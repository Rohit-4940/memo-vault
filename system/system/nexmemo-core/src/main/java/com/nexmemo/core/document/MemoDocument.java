package com.nexmemo.core.document;

import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.Priority;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Document(indexName = "nexmemo_memos")
public class MemoDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String memoNo;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String title;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private MemoStatus status;

    @Field(type = FieldType.Keyword)
    private Priority priority;

    @Field(type = FieldType.Keyword)
    private String memoType;

    @Field(type = FieldType.Keyword)
    private String department;

    @Field(type = FieldType.Keyword)
    private String branch;

    @Field(type = FieldType.Double)
    private BigDecimal amount;

    @Field(type = FieldType.Keyword)
    private String createdBy;

    @Field(type = FieldType.Keyword)
    private String currentApprover;

    @Field(type = FieldType.Integer)
    private Integer approvalLevel;

    @Field(type = FieldType.Integer)
    private Integer totalApprovalLevels;

    @Field(type = FieldType.Date)
    private LocalDateTime createdDate;

    @Field(type = FieldType.Date)
    private LocalDateTime submittedDate;

    @Field(type = FieldType.Date)
    private LocalDateTime approvedDate;
}
