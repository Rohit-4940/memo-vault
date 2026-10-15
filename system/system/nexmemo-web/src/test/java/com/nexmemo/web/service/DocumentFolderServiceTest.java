package com.nexmemo.web.service;

import com.nexmemo.core.dto.DocumentFolderDto;
import com.nexmemo.core.dto.SaveFolderRequest;
import com.nexmemo.core.entity.DocumentFolder;
import com.nexmemo.core.repository.DocumentFolderRepository;
import com.nexmemo.core.repository.DocumentRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the owner-scoped vault folder tree (Enterprise Phase 4): path materialisation,
 * cycle-safe re-parenting, non-empty delete protection and cross-user isolation.
 */
@ExtendWith(MockitoExtension.class)
class DocumentFolderServiceTest {

    @Mock private DocumentFolderRepository folderRepository;
    @Mock private DocumentRecordRepository documentRepository;
    @Mock private DocumentAuthorizationService authz;

    @InjectMocks private DocumentFolderService service;

    private static final Long USER = 10L;

    private DocumentFolder folder(Long id, Long parentId, String path) {
        DocumentFolder f = new DocumentFolder();
        f.setId(id);
        f.setOwnerId(USER);
        f.setParentId(parentId);
        f.setPath(path);
        f.setName("f" + id);
        f.setIsDeleted(false);
        return f;
    }

    private void stubSaveAssignsId() {
        lenient().when(folderRepository.save(any())).thenAnswer(i -> {
            DocumentFolder f = i.getArgument(0);
            if (f.getId() == null) f.setId(42L);
            return f;
        });
    }

    @Test
    void create_rootFolderBuildsOwnPath() {
        stubSaveAssignsId();
        SaveFolderRequest req = new SaveFolderRequest();
        req.setName("Finance");

        DocumentFolderDto dto = service.create(USER, req);

        assertThat(dto.getPath()).isEqualTo("/42/");
        assertThat(dto.getParentId()).isNull();
    }

    @Test
    void create_childFolderAppendsToParentPath() {
        stubSaveAssignsId();
        DocumentFolder parent = folder(5L, null, "/5/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(parent));
        SaveFolderRequest req = new SaveFolderRequest();
        req.setName("2026");
        req.setParentId(5L);

        DocumentFolderDto dto = service.create(USER, req);

        assertThat(dto.getPath()).isEqualTo("/5/42/");
        assertThat(dto.getParentId()).isEqualTo(5L);
    }

    @Test
    void create_rejectsBlankName() {
        SaveFolderRequest req = new SaveFolderRequest();
        req.setName("   ");
        assertThatThrownBy(() -> service.create(USER, req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsUnknownParent() {
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(9L, USER)).thenReturn(Optional.empty());
        when(authz.isAdmin(USER)).thenReturn(false);
        SaveFolderRequest req = new SaveFolderRequest();
        req.setName("x");
        req.setParentId(9L);
        assertThatThrownBy(() -> service.create(USER, req)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void create_enforcesMaximumDepth() {
        DocumentFolder deep = folder(5L, null, "/1/2/3/4/5/6/7/8/9/10/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(deep));
        SaveFolderRequest req = new SaveFolderRequest();
        req.setName("tooDeep");
        req.setParentId(5L);
        assertThatThrownBy(() -> service.create(USER, req)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rename_updatesName() {
        DocumentFolder f = folder(5L, null, "/5/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(f));
        lenient().when(folderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        SaveFolderRequest req = new SaveFolderRequest();
        req.setName("Renamed");

        DocumentFolderDto dto = service.rename(USER, 5L, req);

        assertThat(dto.getName()).isEqualTo("Renamed");
    }

    @Test
    void move_rejectsMovingIntoOwnSubtree() {
        DocumentFolder root = folder(5L, null, "/5/");
        DocumentFolder child = folder(8L, 5L, "/5/8/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(root));
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(8L, USER)).thenReturn(Optional.of(child));

        assertThatThrownBy(() -> service.move(USER, 5L, 8L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void move_rewritesDescendantPaths() {
        DocumentFolder root = folder(5L, null, "/5/");
        DocumentFolder target = folder(3L, null, "/3/");
        DocumentFolder descendant = folder(8L, 5L, "/5/8/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(root));
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(3L, USER)).thenReturn(Optional.of(target));
        when(folderRepository.findByOwnerIdAndIsDeletedFalseOrderByNameAsc(USER))
                .thenReturn(List.of(root, descendant));
        lenient().when(folderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.move(USER, 5L, 3L);

        assertThat(root.getPath()).isEqualTo("/3/5/");
        assertThat(descendant.getPath()).isEqualTo("/3/5/8/");
    }

    @Test
    void delete_rejectsNonEmptyFolder() {
        DocumentFolder f = folder(5L, null, "/5/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(f));
        when(folderRepository.countByParentIdAndIsDeletedFalse(5L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(USER, 5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_rejectsFolderWithDocuments() {
        DocumentFolder f = folder(5L, null, "/5/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(f));
        when(folderRepository.countByParentIdAndIsDeletedFalse(5L)).thenReturn(0L);
        when(documentRepository.countByFolderIdAndIsDeletedFalse(5L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(USER, 5L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_softDeletesEmptyFolder() {
        DocumentFolder f = folder(5L, null, "/5/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(f));
        when(folderRepository.countByParentIdAndIsDeletedFalse(5L)).thenReturn(0L);
        when(documentRepository.countByFolderIdAndIsDeletedFalse(5L)).thenReturn(0L);
        lenient().when(folderRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.delete(USER, 5L);

        assertThat(f.getIsDeleted()).isTrue();
    }

    @Test
    void requireOwned_deniesForeignFolderForNonAdmin() {
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.empty());
        when(authz.isAdmin(USER)).thenReturn(false);

        assertThatThrownBy(() -> service.requireOwned(USER, 5L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void breadcrumb_returnsRootToFolder() {
        DocumentFolder child = folder(8L, 5L, "/5/8/");
        DocumentFolder root = folder(5L, null, "/5/");
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(8L, USER)).thenReturn(Optional.of(child));
        when(folderRepository.findByIdAndOwnerIdAndIsDeletedFalse(5L, USER)).thenReturn(Optional.of(root));

        List<DocumentFolderDto> chain = service.breadcrumb(USER, 8L);

        assertThat(chain).extracting(DocumentFolderDto::getId).containsExactly(5L, 8L);
    }
}
