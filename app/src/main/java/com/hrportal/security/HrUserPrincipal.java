package com.hrportal.security;

import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The signed-in user as Spring Security sees it. Only stable identifiers are kept here. */
public class HrUserPrincipal implements UserDetails {

    private final Long userId;
    private final Long employeeId;
    private final String username;
    private final String passwordHash;
    private final Role role;
    private final boolean enabled;
    private final boolean locked;

    public HrUserPrincipal(UserAccount account, boolean employeeActive) {
        this.userId = account.getId();
        this.employeeId = account.getEmployee() == null ? null : account.getEmployee().getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.role = account.getRole();
        this.enabled = account.isEnabled() && employeeActive;
        this.locked = account.isLocked();
    }

    public Long getUserId() { return userId; }
    public Long getEmployeeId() { return employeeId; }
    public Role getRole() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonLocked() { return !locked; }
    @Override public boolean isEnabled() { return enabled; }
}
