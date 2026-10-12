package com.nexmemo.core.repository;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoCc;
import com.nexmemo.core.entity.User;
import com.nexmemo.core.entity.WorkflowStageInstance;
import com.nexmemo.core.enums.MemoStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Reusable JPA {@link Specification}s for memo queries.
 *
 * <p>{@link #accessibleBy(Long)} is the query-side twin of
 * {@code MemoAuthorizationService.canViewMemo}: it restricts a result set to the memos a user is
 * allowed to see (creator, current approver, CC recipient, or workflow-stage assignee). Keeping the
 * rule expressed once for reads and once for queries avoids the list/detail inconsistency where a
 * user could see a row in a list but be denied on {@code GET /memos/{id}}.</p>
 *
 * <p>Nullable associations are joined with {@link JoinType#LEFT} so memos without a current approver
 * are not silently dropped by the {@code OR} predicate.</p>
 *
 * @author : NexMemo
 **/
public final class MemoSpecifications {

    private MemoSpecifications() {
    }

    public static Specification<Memo> notDeleted() {
        return (root, query, cb) -> cb.isFalse(root.get("isDeleted"));
    }

    public static Specification<Memo> withStatus(MemoStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    /** Case-insensitive contains match on title or memo number. */
    public static Specification<Memo> titleOrMemoNoContains(String term) {
        return (root, query, cb) -> {
            String like = "%" + (term == null ? "" : term.toLowerCase()) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("title")), like),
                    cb.like(cb.lower(root.get("memoNo")), like));
        };
    }

    /**
     * Memos visible to {@code userId}: created by them, currently awaiting their approval, addressed
     * to them as a CC recipient, or assigned to them on a workflow stage.
     */
    public static Specification<Memo> accessibleBy(Long userId) {
        return (root, query, cb) -> {
            Join<Memo, User> approver = root.join("currentApprover", JoinType.LEFT);
            Predicate creator = cb.equal(root.get("createdBy"), userId);
            Predicate currentApprover = cb.equal(approver.get("id"), userId);

            Subquery<Long> ccSub = query.subquery(Long.class);
            Root<MemoCc> cc = ccSub.from(MemoCc.class);
            ccSub.select(cb.literal(1L))
                    .where(cb.equal(cc.get("memo"), root), cb.equal(cc.get("user").get("id"), userId));
            Predicate ccRecipient = cb.exists(ccSub);

            Subquery<Long> stageSub = query.subquery(Long.class);
            Root<WorkflowStageInstance> wsi = stageSub.from(WorkflowStageInstance.class);
            stageSub.select(cb.literal(1L))
                    .where(cb.equal(wsi.get("memo"), root), cb.equal(wsi.get("assignee").get("id"), userId));
            Predicate stageAssignee = cb.exists(stageSub);

            return cb.or(creator, currentApprover, ccRecipient, stageAssignee);
        };
    }
}
