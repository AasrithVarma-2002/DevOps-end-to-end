package com.hrportal.security;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import com.hrportal.repository.UserAccountRepository;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Convenience access to the signed-in user's account and employee record. */
@Component
public class CurrentUser {

    private final UserAccountRepository accounts;

    public CurrentUser(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<HrUserPrincipal> principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof HrUserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public UserAccount account() {
        HrUserPrincipal p = principal().orElseThrow(() -> new IllegalStateException("Not signed in"));
        return accounts.findById(p.getUserId()).orElseThrow(() -> new IllegalStateException("Account removed"));
    }

    /** The signed-in user's employee record; a stand-alone Super Admin has none. */
    public Optional<Employee> employee() {
        return Optional.ofNullable(account().getEmployee());
    }

    public Employee requireEmployee() {
        return employee().orElseThrow(() -> new com.hrportal.service.BusinessRuleException(
                "This account has no employee record"));
    }

    public static boolean hasAtLeast(Role actual, Role required) {
        return actual.ordinal() >= required.ordinal();
    }

    public boolean hasAtLeast(Role required) {
        return principal().map(p -> hasAtLeast(p.getRole(), required)).orElse(false);
    }

    public String username() {
        return principal().map(HrUserPrincipal::getUsername).orElse("system");
    }
}
