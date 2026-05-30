package com.nexmemo.core.enums;

/**
 * @author : Rohit Chapagain
 * @created : 2026-04-03, Friday
 **/
public enum RoleAccess {
    SPECIFIC("Specific"), OWN("Own"), ALL("All");
    private final String value;

    RoleAccess(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
