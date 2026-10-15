package com.nexmemo.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexmemo.core.dto.FormField;
import com.nexmemo.core.entity.MemoTemplate;
import com.nexmemo.core.entity.MemoType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 4c — dynamic memo configuration: form-schema parsing, required-field validation against the
 * memo type + template, amount ceilings, and JSON serialization of captured values.
 */
class MemoFormConfigServiceTest {

    private final MemoFormConfigService service = new MemoFormConfigService(new ObjectMapper());

    private static final String SCHEMA =
            "[{\"key\":\"purpose\",\"label\":\"Purpose\",\"type\":\"textarea\",\"required\":true},"
                    + "{\"key\":\"department\",\"label\":\"Dept\",\"type\":\"select\",\"required\":true,\"options\":[\"IT\",\"HR\"]},"
                    + "{\"key\":\"eventDate\",\"label\":\"Event Date\",\"type\":\"date\"}]";

    private MemoType typeWithSchema(String schema) {
        MemoType t = new MemoType();
        t.setId(1L);
        t.setName("Expense Requisition");
        t.setFormSchema(schema);
        return t;
    }

    @Test
    void parseSchema_returnsFieldsForValidJson() {
        List<FormField> fields = service.parseSchema(typeWithSchema(SCHEMA));
        assertThat(fields).hasSize(3);
        assertThat(fields.get(0).getKey()).isEqualTo("purpose");
        assertThat(fields.get(0).isRequired()).isTrue();
        assertThat(fields.get(1).getOptions()).containsExactly("IT", "HR");
        assertThat(fields.get(2).isRequired()).isFalse();
    }

    @Test
    void parseSchema_returnsEmptyForNullOrBlankSchema() {
        MemoType noSchema = new MemoType();
        noSchema.setName("Plain");
        assertThat(service.parseSchema(noSchema)).isEmpty();
    }

    @Test
    void parseSchema_rejectsMalformedJson() {
        assertThatThrownBy(() -> service.parseSchema(typeWithSchema("not-json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid form schema");
    }

    @Test
    void validateFormValues_enforcesRequiredSchemaFields() {
        MemoType type = typeWithSchema(SCHEMA);
        assertThatThrownBy(() -> service.validateFormValues(type, null,
                Map.of("purpose", "Team outing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'department'");

        service.validateFormValues(type, null, Map.of("purpose", "Team outing", "department", "HR"));
    }

    @Test
    void validateFormValues_enforcesTemplateRequiredFields_andBlankCountsAsMissing() {
        MemoTemplate template = new MemoTemplate();
        template.setName("Travel");
        template.setRequiredFields("[\"destination\",\"costCenter\"]");

        // blank value counts as missing -> first missing key is reported
        assertThatThrownBy(() -> service.validateFormValues(null, template,
                Map.of("destination", "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'destination'");

        // entirely absent key is also flagged
        assertThatThrownBy(() -> service.validateFormValues(null, template,
                Map.of("costCenter", "CC-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'destination'");

        service.validateFormValues(null, template,
                Map.of("destination", "Kathmandu", "costCenter", "CC-01"));
    }

    @Test
    void validateFormValues_allowsUnknownKeys() {
        MemoType type = typeWithSchema(SCHEMA);
        service.validateFormValues(type, null,
                Map.of("purpose", "x", "department", "IT", "unexpectedField", "keep-it"));
    }

    @Test
    void validateAmount_enforcesTemplateCeiling() {
        MemoTemplate template = new MemoTemplate();
        template.setName("Cap");
        template.setMaxAmount(new BigDecimal("5000.00"));

        service.validateAmount(new BigDecimal("5000.00"), template);
        service.validateAmount(null, template);

        assertThatThrownBy(() -> service.validateAmount(new BigDecimal("5000.01"), template))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    void toJson_serializesMap_andNulls() {
        assertThat(service.toJson(null)).isNull();
        assertThat(service.toJson(Map.of("amount", "100"))).isEqualTo("{\"amount\":\"100\"}");
    }
}