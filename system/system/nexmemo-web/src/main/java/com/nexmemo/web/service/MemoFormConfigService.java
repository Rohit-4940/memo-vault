package com.nexmemo.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmemo.core.dto.FormField;
import com.nexmemo.core.entity.MemoTemplate;
import com.nexmemo.core.entity.MemoType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 4c — dynamic memo configuration: parses a memo type's form schema (JSON list of
 * {@link FormField}s), validates the dynamic values captured at memo creation against the type's
 * required fields and the template's required-fields list, and enforces template amount ceilings.
 *
 * <p>Validation is permissive about unknown keys — a configured schema drives requiredness but
 * never rejects extra data.</p>
 *
 * @author : NexMemo
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoFormConfigService {

    private static final TypeReference<List<FormField>> FIELD_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> VALUE_MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /** Parses a memo type's form schema; an empty list when no schema is configured. */
    public List<FormField> parseSchema(MemoType type) {
        if (type == null || type.getFormSchema() == null || type.getFormSchema().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(type.getFormSchema(), FIELD_LIST_TYPE);
        } catch (Exception e) {
            log.warn("MemoType {} has an invalid form_schema: {}", type.getName(), e.getMessage());
            throw new IllegalArgumentException(
                    "MemoType '" + type.getName() + "' has an invalid form schema (must be a JSON array of fields)");
        }
    }

    /** Ensures the schema of a type being created/updated is a parseable JSON array of fields. */
    public void validateSchema(MemoType type) {
        parseSchema(type);
    }

    /** Serializes any dynamic payload (form values, snapshots) to JSON, or null for null input. */
    public String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize memo form values", e);
        }
    }

    /**
     * Validates required dynamic fields at memo creation:
     * <ol>
     *   <li>fields marked {@code required} in the memo type's form schema must be present &amp; non-blank;</li>
     *   <li>keys listed in the template's {@code required_fields} (JSON array of strings) must be present &amp; non-blank.</li>
     * </ol>
     */
    public void validateFormValues(MemoType type, MemoTemplate template, Map<String, Object> formValues) {
        Map<String, Object> values = formValues != null ? formValues : Map.of();

        if (type != null) {
            for (FormField field : parseSchema(type)) {
                if (field.isRequired() && isBlankValue(values.get(field.getKey()))) {
                    throw new IllegalArgumentException(
                            "Required field '" + field.getKey() + "' is missing for memo type '" + type.getName() + "'");
                }
            }
        }

        if (template != null) {
            for (String key : parseRequiredFields(template)) {
                if (isBlankValue(values.get(key))) {
                    throw new IllegalArgumentException(
                            "Required field '" + key + "' is missing for template '" + template.getName() + "'");
                }
            }
        }
    }

    /** Parses the template's JSON {@code required_fields} list of field keys. */
    public List<String> parseRequiredFields(MemoTemplate template) {
        if (template == null || template.getRequiredFields() == null || template.getRequiredFields().isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(template.getRequiredFields(), STRING_LIST_TYPE);
        } catch (Exception e) {
            log.warn("Template {} has an invalid required_fields: {}", template.getName(), e.getMessage());
            throw new IllegalArgumentException("Template '" + template.getName()
                    + "' has an invalid required_fields (must be a JSON array of keys)");
        }
    }

    /**
     * Phase 5b — reads the dynamic values already captured on a memo back into a map, so draft
     * edits can re-validate the full field set (including fields the edit leaves untouched).
     * Blank / unparseable content yields an empty map; legacy memos never fail here.
     */
    public Map<String, Object> parseFormValues(com.nexmemo.core.entity.Memo memo) {
        if (memo == null || memo.getFormValues() == null || memo.getFormValues().isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(memo.getFormValues(), VALUE_MAP_TYPE);
        } catch (Exception e) {
            log.warn("Memo {} has invalid form_values: {}", memo.getId(), e.getMessage());
            return new HashMap<>();
        }
    }

    /** Enforces the template's {@code max_amount} ceiling on the requested amount. */
    public void validateAmount(BigDecimal amount, MemoTemplate template) {
        if (template != null && template.getMaxAmount() != null && amount != null
                && amount.compareTo(template.getMaxAmount()) > 0) {
            throw new IllegalArgumentException("Amount " + amount + " exceeds the maximum allowed for template '"
                    + template.getName() + "' (" + template.getMaxAmount() + ")");
        }
    }

    private boolean isBlankValue(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String s) {
            return s.isBlank();
        }
        return false;
    }
}