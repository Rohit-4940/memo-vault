package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNo(Long documentId, Integer versionNo);

    long countByDocumentId(Long documentId);

    /**
     * Every stored byte across all revisions of the user's live documents — the accurate figure a
     * storage quota must be measured against (a superseded revision still occupies disk).
     *
     * <p>Native SQL on purpose: {@code BaseEntity.id} is declared as the generic {@code PK} (which
     * erases to {@code Serializable}), so an HQL {@code ... in (select d.id ...)} comparison against a
     * {@code Long} column fails Hibernate's static type check. Joining on the real columns avoids that
     * without materialising an unbounded id list.</p>
     */
    @Query(value = "select coalesce(sum(v.size_bytes), 0) from document_version v "
            + "join document_record d on d.id = v.document_id "
            + "where d.owner_id = :ownerId and d.is_deleted = 0", nativeQuery = true)
    long sumLiveVersionBytesByOwner(@Param("ownerId") Long ownerId);
}
