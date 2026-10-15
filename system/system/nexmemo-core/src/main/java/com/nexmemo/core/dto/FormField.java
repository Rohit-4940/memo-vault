package com.nexmemo.core.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * One dynamic form field of a memo type's form schema (Phase 4c). The schema of a memo type is
 * stored as JSON on {@link com.nexmemo.core.entity.MemoType#getFormSchema()} and drives the
 * dynamic creation form; the captured values are stored as JSON on
 * {@link com.nexmemo.core.entity.Memo#getFormValues()}.
 *
 * <p>{@code type} is a free-form widget hint the observability layer maps to a control
 * ({@code text}, {@code textarea}, {@code number}, {@code date}, {@code select}, {@code checkbox},
 * {@code money}, ...).</p>
 *
 * @author : NexMemo
 **/
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FormField {

    /** Machine key used in the memo's {@code form_values} JSON. */
    private String key;

    /** Human-readable label rendered on the form. */
    private String label;

    /** Widget hint (text / textarea / number / date / select / checkbox / money / ...). */
    private String type = "text";

    /** If true, memo creation must supply a non-blank value for this key. */
    private boolean required = false;

    private String placeholder;

    private String defaultValue;

    private List<String> options = new ArrayList<>();
}