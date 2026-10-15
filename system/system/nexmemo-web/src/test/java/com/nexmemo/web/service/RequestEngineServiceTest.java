package com.nexmemo.web.service;

import com.nexmemo.core.dto.RequestTrackingDto;
import com.nexmemo.core.dto.RequestTypeDto;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoType;
import com.nexmemo.core.enums.MemoStatus;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.MemoRepository;
import com.nexmemo.core.repository.MemoTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 5b — the self-service common request engine over the memo engine: the catalogue only
 * exposes active + permitted types, and every raise/edit/submit/cancel/track call is authorized and
 * delegated to {@link MemoService} / the workflow engine.
 */
@ExtendWith(MockitoExtension.class)
class RequestEngineServiceTest {

    @Mock private MemoTypeRepository memoTypeRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private MemoService memoService;
    @Mock private MemoFormConfigService memoFormConfigService;
    @Mock private MemoAuthorizationService memoAuthorizationService;
    @Mock private WorkflowEngineService workflowEngineService;

    @InjectMocks private RequestEngineService service;

    private static MemoType type(long id, String name, Status status) {
        MemoType t = new MemoType();
        t.setId(id);
        t.setName(name);
        t.setStatus(status);
        return t;
    }

    @Test
    void getCatalogue_exposesOnlyActiveTypesTheCallerMayRaise() {
        MemoType active = type(1L, "Leave", Status.ACTIVE);
        MemoType inactive = type(2L, "Retired", Status.INACTIVE);
        MemoType forbidden = type(3L, "Finance", Status.ACTIVE);
        when(memoTypeRepository.findAll()).thenReturn(List.of(active, inactive, forbidden));
        when(memoAuthorizationService.canCreateMemoOfType(10L, 1L)).thenReturn(true);
        when(memoAuthorizationService.canCreateMemoOfType(10L, 3L)).thenReturn(false);
        when(memoFormConfigService.parseSchema(active)).thenReturn(List.of());

        List<RequestTypeDto> catalogue = service.getCatalogue(10L);

        assertThat(catalogue).hasSize(1);
        assertThat(catalogue.get(0).getId()).isEqualTo(1L);
        assertThat(catalogue.get(0).getName()).isEqualTo("Leave");
        verify(memoFormConfigService, never()).parseSchema(inactive);
    }

    @Test
    void createRequest_assertsPermission_andDoesNotSubmitByDefault() {
        MemoService.CreateMemoRequest command = new MemoService.CreateMemoRequest();
        command.setMemoTypeId(1L);
        Memo created = new Memo();
        created.setId(100L);
        when(memoService.createMemo(command, 10L)).thenReturn(created);

        Memo result = service.createRequest(command, false, 10L);

        verify(memoAuthorizationService).assertCanCreateMemoOfType(10L, 1L);
        assertThat(result).isSameAs(created);
        verify(memoService, never()).submitMemo(anyLong(), anyLong());
    }

    @Test
    void createRequest_submitNow_submitsTheFreshDraft() {
        MemoService.CreateMemoRequest command = new MemoService.CreateMemoRequest();
        command.setMemoTypeId(1L);
        Memo created = new Memo();
        created.setId(100L);
        Memo submitted = new Memo();
        submitted.setId(100L);
        submitted.setStatus(MemoStatus.PENDING_APPROVAL);
        when(memoService.createMemo(command, 10L)).thenReturn(created);
        when(memoService.submitMemo(100L, 10L)).thenReturn(submitted);

        Memo result = service.createRequest(command, true, 10L);

        assertThat(result).isSameAs(submitted);
        verify(memoService).submitMemo(100L, 10L);
    }

    @Test
    void updateRequest_assertsEditPermission_andDelegates() {
        MemoService.UpdateMemoRequest command = new MemoService.UpdateMemoRequest();
        Memo updated = new Memo();
        updated.setId(100L);
        when(memoService.updateDraftMemo(100L, command, 10L)).thenReturn(updated);

        Memo result = service.updateRequest(100L, command, 10L);

        verify(memoAuthorizationService).assertCanEditMemo(10L, 100L);
        assertThat(result).isSameAs(updated);
    }

    @Test
    void submitRequest_assertsEditPermission_andDelegates() {
        Memo submitted = new Memo();
        submitted.setId(100L);
        when(memoService.submitMemo(100L, 10L)).thenReturn(submitted);

        Memo result = service.submitRequest(100L, 10L);

        verify(memoAuthorizationService).assertCanEditMemo(10L, 100L);
        assertThat(result).isSameAs(submitted);
    }

    @Test
    void cancelRequest_assertsEditPermission_andDelegates() {
        service.cancelRequest(100L, 10L);

        verify(memoAuthorizationService).assertCanEditMemo(10L, 100L);
        verify(memoService).cancelMemo(100L, 10L);
    }

    @Test
    void getRequest_assertsViewPermission() {
        Memo memo = new Memo();
        memo.setId(100L);
        when(memoRepository.findById(100L)).thenReturn(Optional.of(memo));

        Memo result = service.getRequest(100L, 10L);

        verify(memoAuthorizationService).assertCanViewMemo(10L, 100L);
        assertThat(result).isSameAs(memo);
    }

    @Test
    void trackRequest_assemblesStatusHistoryAndStages() {
        Memo memo = new Memo();
        memo.setId(100L);
        memo.setMemoNo("NEXM-1-1");
        memo.setTitle("Leave request");
        memo.setStatus(MemoStatus.PENDING_APPROVAL);
        memo.setApprovalLevel(0);
        memo.setTotalApprovalLevels(2);
        when(memoRepository.findById(100L)).thenReturn(Optional.of(memo));
        when(memoService.getApprovalHistory(100L)).thenReturn(List.of());
        when(workflowEngineService.getStageInstances(100L)).thenReturn(List.of());

        RequestTrackingDto dto = service.trackRequest(100L, 10L);

        verify(memoAuthorizationService).assertCanViewMemo(10L, 100L);
        assertThat(dto.getMemoId()).isEqualTo(100L);
        assertThat(dto.getMemoNo()).isEqualTo("NEXM-1-1");
        assertThat(dto.getStatus()).isEqualTo(MemoStatus.PENDING_APPROVAL);
        assertThat(dto.getTotalApprovalLevels()).isEqualTo(2);
        verify(memoService).getApprovalHistory(any());
    }
}
