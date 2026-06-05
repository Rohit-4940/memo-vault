package com.nexmemo.core.repository;

import com.nexmemo.core.entity.DocumentTransferFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentTransferFileRepository extends JpaRepository<DocumentTransferFile, Long>, JpaSpecificationExecutor<DocumentTransferFile> {
    List<DocumentTransferFile> findByTransferId(Long transferId);
}
