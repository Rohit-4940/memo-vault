package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentRecord;
import com.nexmemo.core.enums.DocumentClassification;
import com.nexmemo.core.enums.DocumentStatus;
import com.nexmemo.core.enums.PermissionSubjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRecordRepository extends JpaRepository<DocumentRecord, Long> {

    /**
     * Documents the caller can read: their own, plus any that carry a read grant for the caller
     * (as a user, one of their roles, one of their branches or one of their departments), narrowed
     * by the optional catalogue filters (folder, tag, classification, status, free-text search).
     *
     * <p>The subject id lists must be non-empty; callers substitute a sentinel ({@code -1}) so the
     * generated {@code IN ()} predicate is always valid SQL.</p>
     */
    @Query("""
            select distinct d from DocumentRecord d
            where d.isDeleted = false
              and (:includeAll = true
                   or d.ownerId = :userId
                   or exists (
                       select 1 from DocumentPermission p
                       where p.documentId = d.id and p.canRead = true
                         and ((p.subjectType = :userType and p.subjectId = :userId)
                           or (p.subjectType = :roleType and p.subjectId in :roleIds)
                           or (p.subjectType = :branchType and p.subjectId in :branchIds)
                           or (p.subjectType = :deptType and p.subjectId in :departmentIds)
                           or (p.subjectType = :convType and p.subjectId in :conversationIds))))
              and (:folderId is null or d.folderId = :folderId)
              and (:classification is null or d.classification = :classification)
              and (:status is null or d.status = :status)
              and (:tag is null
                   or exists (select 1 from DocumentTag t where t.documentId = d.id and t.tag = :tag))
              and (:search is null
                   or lower(d.title) like lower(concat('%', :search, '%'))
                   or lower(d.originalFilename) like lower(concat('%', :search, '%')))
            """)
    Page<DocumentRecord> findReadable(@Param("userId") Long userId,
                                      @Param("includeAll") boolean includeAll,
                                      @Param("search") String search,
                                      @Param("folderId") Long folderId,
                                      @Param("classification") DocumentClassification classification,
                                      @Param("status") DocumentStatus status,
                                      @Param("tag") String tag,
                                      @Param("userType") PermissionSubjectType userType,
                                      @Param("roleType") PermissionSubjectType roleType,
                                      @Param("branchType") PermissionSubjectType branchType,
                                      @Param("deptType") PermissionSubjectType deptType,
                                      @Param("convType") PermissionSubjectType convType,
                                      @Param("roleIds") List<Long> roleIds,
                                      @Param("branchIds") List<Long> branchIds,
                                      @Param("departmentIds") List<Long> departmentIds,
                                      @Param("conversationIds") List<Long> conversationIds,
                                      Pageable pageable);

    List<DocumentRecord> findByOwnerIdAndIsDeletedFalseOrderByCreatedDateDesc(Long ownerId);

    List<DocumentRecord> findBySha256AndIsDeletedFalse(String sha256);

    long countByOwnerIdAndIsDeletedFalse(Long ownerId);

    long countByFolderIdAndIsDeletedFalse(Long folderId);

    /** Total live bytes owned by a user — used for quota enforcement. */
    @Query("select coalesce(sum(d.sizeBytes), 0) from DocumentRecord d "
            + "where d.ownerId = :ownerId and d.isDeleted = false")
    long sumLiveSizeByOwner(@Param("ownerId") Long ownerId);
}
