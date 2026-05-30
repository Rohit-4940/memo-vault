package com.nexmemo.core.enums;

/**
 * @author : Rohit Chapagain
 * @created : 2026-04-03, Friday
 **/
public enum Status {
    INACTIVE("Inactive"), ACTIVE("Active"), DELETED("Deleted"), LOCKED("Locaked");

    private final String value;

    Status(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
