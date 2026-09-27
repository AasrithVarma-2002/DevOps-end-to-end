package com.hrportal.domain;

/**
 * Roles are hierarchical: each role can do everything the roles below it can
 * (SUPER_ADMIN > HR_ADMIN > MANAGER > EMPLOYEE), see SecurityConfig.
 */
public enum Role {
    EMPLOYEE("Employee"),
    MANAGER("Manager"),
    HR_ADMIN("HR Admin"),
    SUPER_ADMIN("Super Admin");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
