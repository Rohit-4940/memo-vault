package com.nexmemo.core.entity;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "workflow_stage")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class WorkflowStage extends BaseEntity<Long> implements Serializable {

    @Column(nullable = false)
    private String name;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(length = 50)
    private String color;

    @Column(length = 100)
    private String icon;

    @Column(name = "sla_hours")
    private Integer slaHours;

    @Column(name = "require_comment", nullable = false)
    private Boolean requireComment = true;

    @Column(name = "require_document", nullable = false)
    private Boolean requireDocument = false;

    @Column(name = "require_digital_signature", nullable = false)
    private Boolean requireDigitalSignature = false;

    @Column(name = "require_acknowledgement", nullable = false)
    private Boolean requireAcknowledgement = false;

    @Column(name = "enable_escalation", nullable = false)
    private Boolean enableEscalation = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escalation_rule_id")
    private EscalationRule escalationRule;

    @Column(name = "notify_email", nullable = false)
    private Boolean notifyEmail = false;

    @Column(name = "notify_sms", nullable = false)
    private Boolean notifySms = false;

    @Column(name = "notify_in_app", nullable = false)
    private Boolean notifyInApp = true;
}
