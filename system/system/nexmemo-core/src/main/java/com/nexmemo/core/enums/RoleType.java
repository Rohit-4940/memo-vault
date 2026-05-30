package com.nexmemo.core.enums;

/**
 * @author : Rohit Chapagain
 * @created : 2026-04-03, Friday
 **/
public enum RoleType {
    MAKER("Maker"), APPROVAL("Approval"), COMMITTEE("Committee") ,ADMIN("Admin"),USER("User"),SUPER_ADMIN("SUPER ADMIN");

    private final String value;

    RoleType(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
