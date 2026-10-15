package com.nexmemo.web.service;

import com.nexmemo.core.entity.NotificationConfig;
import com.nexmemo.core.enums.NotificationEventType;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders notification subjects/bodies (Phase 4a).
 *
 * <p>Templates come from {@code notification_config} when an administrator has configured one for
 * the event, otherwise a sensible built-in default is used so notifications work out of the box.
 * Placeholders use the lightweight {@code ${name}} syntax and are resolved from the event variables;
 * unknown placeholders render as an empty string rather than leaking {@code ${...}} into emails.</p>
 *
 * @author : NexMemo
 **/
@Component
public class NotificationTemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    public String render(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public String resolveSubject(NotificationConfig config, NotificationEventType type, Map<String, Object> vars) {
        String template = (config != null && hasText(config.getTemplateSubject()))
                ? config.getTemplateSubject()
                : defaultSubject(type);
        return render(template, vars);
    }

    public String resolveBody(NotificationConfig config, NotificationEventType type, Map<String, Object> vars) {
        String template = (config != null && hasText(config.getTemplateBody()))
                ? config.getTemplateBody()
                : defaultBody(type);
        return render(template, vars);
    }

    static String defaultSubject(NotificationEventType type) {
        if (type == null) {
            return "NexMemo notification";
        }
        return switch (type) {
            case MEMO_CREATED -> "Memo ${memoNo} created";
            case MEMO_SUBMITTED -> "Action required: memo ${memoNo} awaits your approval";
            case MEMO_APPROVED -> "Memo ${memoNo} has been approved";
            case MEMO_REJECTED -> "Memo ${memoNo} was rejected";
            case MEMO_RETURNED -> "Memo ${memoNo} was returned for revision";
            case MEMO_FORWARDED -> "Memo ${memoNo} was forwarded to you";
            case MEMO_DELEGATED -> "Memo ${memoNo} was delegated to you";
            case MEMO_ESCALATED -> "Memo ${memoNo} has been escalated";
            case SLA_BREACH -> "SLA breach on memo ${memoNo}";
            case COMMENT_ADDED -> "New comment on memo ${memoNo}";
            case DOCUMENT_UPLOADED -> "New document on memo ${memoNo}";
        };
    }

    static String defaultBody(NotificationEventType type) {
        String lead = switch (type == null ? NotificationEventType.MEMO_CREATED : type) {
            case MEMO_CREATED -> "Memo <strong>${memoNo}</strong> — ${memoTitle} has been created.";
            case MEMO_SUBMITTED -> "Memo <strong>${memoNo}</strong> — ${memoTitle} is awaiting your approval.";
            case MEMO_APPROVED -> "Memo <strong>${memoNo}</strong> — ${memoTitle} has been fully approved.";
            case MEMO_REJECTED -> "Memo <strong>${memoNo}</strong> was rejected by ${actorName}.";
            case MEMO_RETURNED -> "Memo <strong>${memoNo}</strong> was returned for revision by ${actorName}.";
            case MEMO_FORWARDED -> "Memo <strong>${memoNo}</strong> was forwarded to you by ${actorName}.";
            case MEMO_DELEGATED -> "Memo <strong>${memoNo}</strong> was delegated to you by ${actorName}.";
            case MEMO_ESCALATED -> "Memo <strong>${memoNo}</strong> has been escalated.";
            case SLA_BREACH -> "Memo <strong>${memoNo}</strong> has breached its SLA.";
            case COMMENT_ADDED -> "${actorName} added a comment on memo <strong>${memoNo}</strong>.";
            case DOCUMENT_UPLOADED -> "${actorName} uploaded a document to memo <strong>${memoNo}</strong>.";
        };
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;font-size:14px;color:#222\">"
                + "<p>" + lead + "</p>"
                + "<p><em>${remarks}</em></p>"
                + "<p><a href=\"${link}\">Open memo ${memoNo}</a></p>"
                + "</div>";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
