package com.nexmemo.web.service;

import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.repository.ApprovalHistoryRepository;
import com.nexmemo.core.repository.BudgetAllocationRepository;
import com.nexmemo.core.repository.DepartmentRepository;
import com.nexmemo.core.repository.LeaveTrackerRepository;
import com.nexmemo.core.repository.MemoCategoryRepository;
import com.nexmemo.core.repository.MemoCcRepository;
import com.nexmemo.core.repository.MemoDocumentRepository;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.MemoRoutingRepository;
import com.nexmemo.core.repository.MemoTemplateRepository;
import com.nexmemo.core.repository.MemoTypeRepository;
import com.nexmemo.security.repository.BranchRepository;
import com.nexmemo.security.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Verifies that the memo list/search endpoints are scoped to accessible memos for non-admins and
 * remain unscoped for admins (Phase 3.1 — removes the list/detail authorization inconsistency).
 */
@ExtendWith(MockitoExtension.class)
class MemoServiceTest {

    @Mock private MemoRepository memoRepository;
    @Mock private MemoRoutingRepository memoRoutingRepository;
    @Mock private MemoDocumentRepository memoDocumentRepository;
    @Mock private ApprovalHistoryRepository approvalHistoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private MemoTypeRepository memoTypeRepository;
    @Mock private BudgetAllocationRepository budgetAllocationRepository;
    @Mock private LeaveTrackerRepository leaveTrackerRepository;
    @Mock private MemoCcRepository memoCcRepository;
    @Mock private MemoTemplateRepository memoTemplateRepository;
    @Mock private ApprovalEngineService approvalEngineService;
    @Mock private NotificationService notificationService;
    @Mock private com.nexmemo.web.storage.StorageService storageService;
    @Mock private WorkflowEngineService workflowEngineService;
    @Mock private MemoNumberGeneratorService memoNumberGeneratorService;
    @Mock private MemoCategoryRepository memoCategoryRepository;
    @Mock private AuditEventPublisher auditEventPublisher;
    @Mock private MemoAuthorizationService memoAuthorizationService;

    @InjectMocks private MemoService service;

    private static final Long ADMIN = 1L;
    private static final Long USER = 10L;

    @Test
    void getAllMemos_adminSeesAll() {
        when(memoAuthorizationService.isAdmin(ADMIN)).thenReturn(true);
        when(memoRepository.findAll()).thenReturn(List.of(new Memo()));

        assertThat(service.getAllMemos(ADMIN)).hasSize(1);
        verify(memoRepository).findAll();
        verify(memoRepository, never()).findAll(any(Specification.class));
    }

    @Test
    void getAllMemos_nonAdminUsesAccessibleSpecification() {
        when(memoAuthorizationService.isAdmin(USER)).thenReturn(false);
        when(memoRepository.findAll(any(Specification.class))).thenReturn(List.of(new Memo()));

        assertThat(service.getAllMemos(USER)).hasSize(1);
        verify(memoRepository).findAll(any(Specification.class));
        verify(memoRepository, never()).findAll();
    }

    @Test
    void getAllMemosPaginated_nonAdminUsesAccessibleSpecification() {
        Pageable pageable = PageRequest.of(0, 20);
        when(memoAuthorizationService.isAdmin(USER)).thenReturn(false);
        when(memoRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(Page.empty());

        Page<Memo> result = service.getAllMemosPaginated(USER, pageable);

        assertThat(result).isEmpty();
        verify(memoRepository).findAll(any(Specification.class), eq(pageable));
        verify(memoRepository, never()).findAllActive(any(Pageable.class));
    }

    @Test
    void getAllMemosPaginated_adminUsesActive() {
        Pageable pageable = PageRequest.of(0, 20);
        when(memoAuthorizationService.isAdmin(ADMIN)).thenReturn(true);
        when(memoRepository.findAllActive(pageable)).thenReturn(Page.empty());

        service.getAllMemosPaginated(ADMIN, pageable);

        verify(memoRepository).findAllActive(pageable);
        verify(memoRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void getMemosByStatus_nonAdminScoped_adminUnscoped() {
        when(memoAuthorizationService.isAdmin(USER)).thenReturn(false);
        when(memoRepository.findAll(any(Specification.class))).thenReturn(List.of());
        service.getMemosByStatus(USER, MemoStatus.PENDING_APPROVAL);
        verify(memoRepository).findAll(any(Specification.class));

        when(memoAuthorizationService.isAdmin(ADMIN)).thenReturn(true);
        when(memoRepository.findByStatus(MemoStatus.PENDING_APPROVAL)).thenReturn(List.of());
        service.getMemosByStatus(ADMIN, MemoStatus.PENDING_APPROVAL);
        verify(memoRepository).findByStatus(MemoStatus.PENDING_APPROVAL);
    }

    @Test
    void searchMemos_nonAdminScoped_adminUnscoped() {
        when(memoAuthorizationService.isAdmin(USER)).thenReturn(false);
        when(memoRepository.findAll(any(Specification.class))).thenReturn(List.of());
        service.searchMemos(USER, "budget");
        verify(memoRepository).findAll(any(Specification.class));

        when(memoAuthorizationService.isAdmin(ADMIN)).thenReturn(true);
        when(memoRepository.findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase("budget"))
                .thenReturn(List.of());
        service.searchMemos(ADMIN, "budget");
        verify(memoRepository).findByTitleContainingIgnoreCaseOrMemoNoContainingIgnoreCase("budget");
    }
}
