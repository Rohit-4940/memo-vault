package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentFolderRepository extends JpaRepository<DocumentFolder, Long> {

    List<DocumentFolder> findByOwnerIdAndIsDeletedFalseOrderByNameAsc(Long ownerId);

    Optional<DocumentFolder> findByIdAndOwnerIdAndIsDeletedFalse(Long id, Long ownerId);

    boolean existsByParentIdAndIsDeletedFalse(Long parentId);

    long countByParentIdAndIsDeletedFalse(Long parentId);
}
