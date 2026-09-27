package com.hrportal.web;

import com.hrportal.domain.Role;

/** What every page needs to know about the signed-in user (menu, name, bell count). */
public record CurrentUserView(String displayName, Role role, Long employeeId, long unreadNotifications) {

    public boolean isManager() {
        return role.ordinal() >= Role.MANAGER.ordinal();
    }

    public boolean isHr() {
        return role.ordinal() >= Role.HR_ADMIN.ordinal();
    }

    public boolean isSuperAdmin() {
        return role == Role.SUPER_ADMIN;
    }

    public boolean hasEmployeeRecord() {
        return employeeId != null;
    }
}
