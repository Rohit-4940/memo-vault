package com.nexmemo.core.entity;

import com.nexmemo.core.enums.MemoRoutingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;

@Entity
@Table(name = "memo_routing", indexes = {
    @Index(name = "idx_memo_routing_memo_id", columnList = "memo_id"),
    @Index(name = "idx_memo_routing_to_branch", columnList = "to_branch_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class MemoRouting extends BaseEntity<Long> implements Serializable {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memo_id", nullable = false)
    private Memo memo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_branch_id")
    private Branch fromBranch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_branch_id", nullable = false)
    private Branch toBranch;

    @Column(name = "routing_order", nullable = false)
    private Integer routingOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemoRoutingStatus status = MemoRoutingStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String remarks;
}
