package com.nexmemo.web.service;

import com.nexmemo.core.entity.NotificationConfig;
import com.nexmemo.core.enums.NotificationChannel;
import com.nexmemo.core.enums.NotificationEventType;
import com.nexmemo.core.event.MemoNotificationEvent;
import com.nexmemo.core.repository.NotificationConfigRepository;
import com.nexmemo.security.service.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Phase 4a — verifies delivery semantics: default + configured templates, disabled-channel skipping,
 * missing-email skipping, and that a transport failure is recorded without ever propagating (so a
 * committed approval is never rolled back by a mail problem).
 */
@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock private NotificationConfigRepository notificationConfigRepository;
    @Mock private NotificationLogService notificationLogService;
    @Mock private NotificationTemplateRenderer renderer;
    @Mock private EmailService emailService;

    @InjectMocks private NotificationDispatchService service;

    private static final NotificationEventType TYPE = NotificationEventType.MEMO_SUBMITTED;

    private MemoNotificationEvent event() {
        return MemoNotificationEvent.builder()
                .eventType(TYPE)
                .memoId(5L).memoNo("NEXM-5").memoTitle("Title")
                .recipientId(2L).recipientEmail("approver@x.com").recipientName("Approver")
                .actorId(1L).actorName("Creator").remarks("please review")
                .build();
    }

    private void stubConfig(NotificationConfig config) {
        when(notificationConfigRepository.findByEventTypeAndChannel(TYPE, NotificationChannel.EMAIL))
                .thenReturn(Optional.ofNullable(config));
    }

    @Test
    void dispatch_sendsEmailAndLogsSent_whenNoConfigUsesDefaults() {
        stubConfig(null);
        when(renderer.resolveSubject(isNull(), eq(TYPE), anyMap())).thenReturn("Subject");
        when(renderer.resolveBody(isNull(), eq(TYPE), anyMap())).thenReturn("<p>Body</p>");

        service.dispatch(event());

        verify(emailService).sendHtml("approver@x.com", "Subject", "<p>Body</p>");
        verify(notificationLogService).logNotification(5L, 2L, TYPE,
                NotificationChannel.EMAIL, "SENT", null);
    }

    @Test
    void dispatch_usesConfiguredTemplate_whenActiveConfigPresent() {
        NotificationConfig config = new NotificationConfig();
        config.setIsActive(true);
        stubConfig(config);
        when(renderer.resolveSubject(eq(config), eq(TYPE), anyMap())).thenReturn("Configured");
        when(renderer.resolveBody(eq(config), eq(TYPE), anyMap())).thenReturn("<p>Configured</p>");

        service.dispatch(event());

        verify(emailService).sendHtml("approver@x.com", "Configured", "<p>Configured</p>");
        verify(notificationLogService).logNotification(5L, 2L, TYPE,
                NotificationChannel.EMAIL, "SENT", null);
    }

    @Test
    void dispatch_skipsAndLogs_whenChannelDisabled() {
        NotificationConfig config = new NotificationConfig();
        config.setIsActive(false);
        stubConfig(config);

        service.dispatch(event());

        verifyNoInteractions(emailService);
        verify(renderer, never()).resolveSubject(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), anyMap());
        verify(notificationLogService).logNotification(5L, 2L, TYPE,
                NotificationChannel.EMAIL, "SKIPPED", "Channel disabled by notification config");
    }

    @Test
    void dispatch_logsFailedAndSwallows_whenEmailThrows() {
        stubConfig(null);
        when(renderer.resolveSubject(isNull(), eq(TYPE), anyMap())).thenReturn("Subject");
        when(renderer.resolveBody(isNull(), eq(TYPE), anyMap())).thenReturn("<p>Body</p>");
        doThrow(new MailSendException("smtp down"))
                .when(emailService).sendHtml(anyString(), anyString(), anyString());

        assertThatCode(() -> service.dispatch(event())).doesNotThrowAnyException();

        verify(notificationLogService).logNotification(5L, 2L, TYPE,
                NotificationChannel.EMAIL, "FAILED", "smtp down");
    }

    @Test
    void dispatch_skipsRecipientWithoutEmail() {
        MemoNotificationEvent noEmail = MemoNotificationEvent.builder()
                .eventType(TYPE).memoId(5L).memoNo("NEXM-5")
                .recipientId(2L).recipientEmail(null).recipientName("NoMail")
                .build();

        service.dispatch(noEmail);

        verifyNoInteractions(emailService, notificationConfigRepository);
        verify(notificationLogService).logNotification(5L, 2L, TYPE,
                NotificationChannel.EMAIL, "SKIPPED", "Recipient has no email address");
    }
}
