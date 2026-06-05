package com.nexmemo.core.specification;

import java.util.List;

public class FilterCriteria {
    private String field;
    private FilterOperator operator;
    private String value;
    private List<String> values;

    public FilterCriteria() {}

    public FilterCriteria(String field, FilterOperator operator, String value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public FilterCriteria(String field, FilterOperator operator, String value, List<String> values) {
        this.field = field;
        this.operator = operator;
        this.value = value;
        this.values = values;
    }

    public String getField() { return field; }
    public void setField(String field) { this.field = field; }
    public FilterOperator getOperator() { return operator; }
    public void setOperator(FilterOperator operator) { this.operator = operator; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public List<String> getValues() { return values; }
    public void setValues(List<String> values) { this.values = values; }
}
