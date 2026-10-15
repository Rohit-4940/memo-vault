package com.nexmemo.web.service;

import com.nexmemo.core.entity.NotificationConfig;
import com.nexmemo.core.enums.NotificationEventType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4a — template rendering: placeholder substitution, default fallbacks and graceful handling
 * of unknown/blank values.
 */
class NotificationTemplateRendererTest {

    private final NotificationTemplateRenderer renderer = new NotificationTemplateRenderer();

    @Test
    void render_replacesKnownPlaceholders() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("recipientName", "Ann");
        vars.put("memoNo", "NEXM-1");

        assertThat(renderer.render("Hi ${recipientName}, memo ${memoNo}", vars))
                .isEqualTo("Hi Ann, memo NEXM-1");
    }

    @Test
    void render_unknownPlaceholderBecomesEmpty() {
        assertThat(renderer.render("X=${missing}", Map.of())).isEqualTo("X=");
    }

    @Test
    void render_nullTemplateReturnsNull() {
        assertThat(renderer.render(null, Map.of())).isNull();
    }

    @Test
    void resolveSubject_usesDefaultWhenNoConfig() {
        String subject = renderer.resolveSubject(null, NotificationEventType.MEMO_SUBMITTED,
                Map.of("memoNo", "NEXM-9"));

        assertThat(subject).contains("NEXM-9").contains("awaits your approval");
    }

    @Test
    void resolveSubject_usesConfiguredTemplateOverDefault() {
        NotificationConfig config = new NotificationConfig();
        config.setTemplateSubject("Custom: ${memoNo}");

        String subject = renderer.resolveSubject(config, NotificationEventType.MEMO_APPROVED,
                Map.of("memoNo", "N-7"));

        assertThat(subject).isEqualTo("Custom: N-7");
    }

    @Test
    void resolveBody_blankConfiguredBodyFallsBackToDefault() {
        NotificationConfig config = new NotificationConfig();
        config.setTemplateBody("   ");

        Map<String, Object> vars = new HashMap<>();
        vars.put("memoNo", "N-3");
        vars.put("actorName", "Bob");
        vars.put("memoTitle", "Travel");
        vars.put("remarks", "not approved");
        vars.put("link", "");

        String body = renderer.resolveBody(config, NotificationEventType.MEMO_REJECTED, vars);

        assertThat(body).contains("N-3").contains("Bob");
    }
}
