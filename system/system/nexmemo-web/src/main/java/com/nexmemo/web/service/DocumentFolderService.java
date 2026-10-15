package com.nexmemo.web.service;

import com.nexmemo.core.dto.DocumentFolderDto;
import com.nexmemo.core.dto.SaveFolderRequest;
import com.nexmemo.core.entity.DocumentFolder;
import com.nexmemo.core.repository.DocumentFolderRepository;
import com.nexmemo.core.repository.DocumentRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owner-scoped vault folder tree (Enterprise Phase 4).
 *
 * <p>Folders organise a user's own catalogue. They are deliberately <b>not</b> a permission
 * boundary: a folder is only ever visible to its owner (or an administrator), and a document's
 * readability is still decided per document. Deleting a folder is refused while it is non-empty so a
 * mis-click cannot silently orphan documents.</p>
 *
 * @author : NexMemo
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentFolderService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_DEPTH = 10;

    private final DocumentFolderRepository folderRepository;
    private final DocumentRecordRepository documentRepository;
    private final DocumentAuthorizationService authz;

    @Transactional(readOnly = true)
    public List<DocumentFolderDto> list(Long userId) {
        return folderRepository.findByOwnerIdAndIsDeletedFalseOrderByNameAsc(userId).stream()
                .map(f -> toDto(f, documentRepository.countByFolderIdAndIsDeletedFalse(f.getId())))
                .toList();
    }

    @Transactional
    public DocumentFolderDto create(Long userId, SaveFolderRequest request) {
        String name = validateName(request == null ? null : request.getName());
        Long parentId = request == null ? null : request.getParentId();
        DocumentFolder parent = parentId == null ? null : requireOwned(userId, parentId);
        if (parent != null && depthOf(parent) >= MAX_DEPTH) {
            throw new IllegalArgumentException("Folder nesting is limited to " + MAX_DEPTH + " levels");
        }

        DocumentFolder folder = new DocumentFolder();
        folder.setOwnerId(userId);
        folder.setParentId(parent == null ? null : parent.getId());
        folder.setName(name);
        folder.setIsDeleted(false);
        folder.setPath("/");
        folder = folderRepository.save(folder);
        folder.setPath((parent == null ? "/" : parent.getPath()) + folder.getId() + "/");
        folder = folderRepository.save(folder);
        log.debug("Created folder#{} '{}' for user#{}", folder.getId(), name, userId);
        return toDto(folder, 0L);
    }

    @Transactional
    public DocumentFolderDto rename(Long userId, Long folderId, SaveFolderRequest request) {
        DocumentFolder folder = requireOwned(userId, folderId);
        folder.setName(validateName(request == null ? null : request.getName()));
        folder = folderRepository.save(folder);
        return toDto(folder, documentRepository.countByFolderIdAndIsDeletedFalse(folder.getId()));
    }

    @Transactional
    public DocumentFolderDto move(Long userId, Long folderId, Long newParentId) {
        DocumentFolder folder = requireOwned(userId, folderId);
        DocumentFolder parent = newParentId == null ? null : requireOwned(userId, newParentId);

        if (parent != null) {
            if (parent.getId().equals(folder.getId()) || parent.getPath().startsWith(folder.getPath())) {
                throw new IllegalArgumentException("A folder cannot be moved into itself or its own sub-folder");
            }
            if (depthOf(parent) >= MAX_DEPTH) {
                throw new IllegalArgumentException("Folder nesting is limited to " + MAX_DEPTH + " levels");
            }
        }

        String oldPrefix = folder.getPath();
        String newPrefix = (parent == null ? "/" : parent.getPath()) + folder.getId() + "/";

        folder.setParentId(parent == null ? null : parent.getId());
        folder.setPath(newPrefix);
        folder = folderRepository.save(folder);

        // Re-point every descendant so its materialised ancestor chain stays correct.
        for (DocumentFolder candidate : folderRepository.findByOwnerIdAndIsDeletedFalseOrderByNameAsc(userId)) {
            if (candidate.getId().equals(folder.getId())) {
                continue;
            }
            if (candidate.getPath().startsWith(oldPrefix)) {
                candidate.setPath(newPrefix + candidate.getPath().substring(oldPrefix.length()));
                folderRepository.save(candidate);
            }
        }
        return toDto(folder, documentRepository.countByFolderIdAndIsDeletedFalse(folder.getId()));
    }

    @Transactional
    public void delete(Long userId, Long folderId) {
        DocumentFolder folder = requireOwned(userId, folderId);
        if (folderRepository.countByParentIdAndIsDeletedFalse(folder.getId()) > 0) {
            throw new IllegalArgumentException("Folder is not empty: delete or move its sub-folders first");
        }
        long docs = documentRepository.countByFolderIdAndIsDeletedFalse(folder.getId());
        if (docs > 0) {
            throw new IllegalArgumentException("Folder is not empty: it still contains " + docs + " document(s)");
        }
        folder.setIsDeleted(true);
        folderRepository.save(folder);
        log.debug("Soft-deleted folder#{} for user#{}", folderId, userId);
    }

    /** Breadcrumb from the root down to the given folder. */
    @Transactional(readOnly = true)
    public List<DocumentFolderDto> breadcrumb(Long userId, Long folderId) {
        DocumentFolder folder = requireOwned(userId, folderId);
        List<DocumentFolderDto> chain = new ArrayList<>();
        DocumentFolder current = folder;
        while (current != null) {
            chain.add(0, toDto(current, null));
            current = current.getParentId() == null ? null : requireOwned(userId, current.getParentId());
        }
        return chain;
    }

    /** Loads a folder the caller owns (or any live folder for an administrator). */
    @Transactional(readOnly = true)
    public DocumentFolder requireOwned(Long userId, Long folderId) {
        if (folderId == null) {
            throw new IllegalArgumentException("Folder id is required");
        }
        Optional<DocumentFolder> owned = folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(folderId, userId);
        if (owned.isPresent()) {
            return owned.get();
        }
        if (authz.isAdmin(userId)) {
            return folderRepository.findById(folderId)
                    .filter(f -> !Boolean.TRUE.equals(f.getIsDeleted()))
                    .orElseThrow(() -> new AccessDeniedException("Folder not found or not accessible"));
        }
        throw new AccessDeniedException("Folder not found or not accessible");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        String cleaned = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Folder name is required");
        }
        return cleaned.length() > MAX_NAME_LENGTH ? cleaned.substring(0, MAX_NAME_LENGTH) : cleaned;
    }

    private int depthOf(DocumentFolder folder) {
        String path = folder.getPath();
        if (path == null || path.isBlank()) {
            return 0;
        }
        int depth = 0;
        for (String part : path.split("/")) {
            if (!part.isBlank()) {
                depth++;
            }
        }
        return depth;
    }

    private DocumentFolderDto toDto(DocumentFolder f, Long documentCount) {
        return DocumentFolderDto.builder()
                .id(f.getId())
                .parentId(f.getParentId())
                .name(f.getName())
                .path(f.getPath())
                .ownerId(f.getOwnerId())
                .documentCount(documentCount)
                .createdDate(f.getCreatedDate())
                .build();
    }
}
