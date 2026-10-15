package com.nexmemo.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmemo.core.entity.Memo;
import com.nexmemo.core.entity.MemoVersion;
import com.nexmemo.core.repository.MemoVersionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase 4c — memo versioning: the previously dead {@code memo_version} capability now receives a
 * v1 snapshot at creation and appends later versions, serializing content + dynamic form values.
 */
@ExtendWith(MockitoExtension.class)
class MemoVersionServiceTest {

    @Mock private MemoVersionRepository memoVersionRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private MemoVersionService service;

    private Memo memo() {
        Memo m = new Memo();
        m.setId(10L);
        m.setMemoNo("NEXM-1-0001");
        m.setTitle("Branch visit");
        m.setDescription("desc");
        m.setContent("<p>body</p>");
        m.setFormValues("{\"purpose\":\"visit\",\"department\":\"IT\"}");
        return m;
    }

    @Test
    void snapshot_createsVersionOneWithJsonContent() {
        when(memoVersionRepository.findTopByMemoIdOrderByVersionNumberDesc(10L)).thenReturn(Optional.empty());
        when(memoVersionRepository.save(any(MemoVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoVersion version = service.snapshot(memo(), 5L);

        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThat(version.getCreatedBy()).isEqualTo(5L);
        assertThat(version.getMemo().getId()).isEqualTo(10L);
        assertThat(version.getContentJson()).contains("\"memoNo\":\"NEXM-1-0001\"");
        assertThat(version.getContentJson()).contains("\"purpose\":\"visit\"");
    }

    @Test
    void snapshot_appendsNextVersionWhenHistoryExists() {
        MemoVersion latest = new MemoVersion();
        latest.setVersionNumber(2);
        when(memoVersionRepository.findTopByMemoIdOrderByVersionNumberDesc(10L)).thenReturn(Optional.of(latest));
        when(memoVersionRepository.save(any(MemoVersion.class))).thenAnswer(inv -> inv.getArgument(0));

        MemoVersion version = service.snapshot(memo(), null);

        assertThat(version.getVersionNumber()).isEqualTo(3);
        assertThat(version.getCreatedBy()).isEqualTo(0L);
    }
}