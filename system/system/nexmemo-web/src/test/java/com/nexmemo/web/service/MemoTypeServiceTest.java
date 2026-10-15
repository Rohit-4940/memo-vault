package com.nexmemo.web.service;

import com.nexmemo.core.entity.MemoType;
import com.nexmemo.core.enums.Status;
import com.nexmemo.core.repository.MemoTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4c — memo type administration: form schema is validated on create/update and preserved when
 * an update omits it; the parsed schema is delegated to the form-config service.
 */
@ExtendWith(MockitoExtension.class)
class MemoTypeServiceTest {

    @Mock private MemoTypeRepository memoTypeRepository;
    @Mock private MemoFormConfigService memoFormConfigService;

    @InjectMocks private MemoTypeService service;

    @Test
    void update_copiesSchemaAndPreservesItWhenOmitted() {
        MemoType existing = new MemoType();
        existing.setId(1L);
        existing.setName("Expense");
        existing.setFormSchema("[{\"key\":\"a\",\"label\":\"A\"}]");
        when(memoTypeRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(memoTypeRepository.save(any(MemoType.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoType dtoWithoutSchema = new MemoType();
        dtoWithoutSchema.setName("Expense");

        service.updateMemoType(1L, dtoWithoutSchema);
        assertThat(existing.getFormSchema()).isEqualTo("[{\"key\":\"a\",\"label\":\"A\"}]");
        verify(memoFormConfigService, never()).validateSchema(any());

        MemoType dtoWithSchema = new MemoType();
        dtoWithSchema.setName("Expense");
        dtoWithSchema.setFormSchema("[{\"key\":\"x\",\"label\":\"X\"}]");

        service.updateMemoType(1L, dtoWithSchema);
        assertThat(existing.getFormSchema()).isEqualTo("[{\"key\":\"x\",\"label\":\"X\"}]");
        verify(memoFormConfigService).validateSchema(any());
    }

    @Test
    void create_defaultsStatusToActive_andValidatesSchema() {
        MemoType type = new MemoType();
        type.setName("Leave");
        when(memoTypeRepository.save(any(MemoType.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoType saved = service.createMemoType(type);

        assertThat(saved.getStatus()).isEqualTo(Status.ACTIVE);
        verify(memoFormConfigService).validateSchema(any());
    }

    @Test
    void getFormSchema_delegatesToConfigService() {
        MemoType type = new MemoType();
        type.setId(1L);
        type.setName("Expense");
        when(memoTypeRepository.findById(1L)).thenReturn(Optional.of(type));

        service.getFormSchema(1L);

        verify(memoFormConfigService).parseSchema(type);
    }
}