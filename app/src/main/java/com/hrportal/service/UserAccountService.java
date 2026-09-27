package com.hrportal.service;

import com.hrportal.domain.Employee;
import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import com.hrportal.repository.UserAccountRepository;
import com.hrportal.security.CurrentUser;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserAccountService {

    private final UserAccountRepository accounts;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final NotificationService notifications;
    private final CurrentUser currentUser;
    private final Clock clock;

    public UserAccountService(UserAccountRepository accounts, PasswordEncoder encoder, AuditService audit,
                              NotificationService notifications, CurrentUser currentUser, Clock clock) {
        this.accounts = accounts;
        this.encoder = encoder;
        this.audit = audit;
        this.notifications = notifications;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /** Creates the login for a new employee. Returns the temporary password to hand over. */
    public String createForEmployee(Employee employee, Role role) {
        if (accounts.existsByUsernameIgnoreCase(employee.getEmail())) {
            throw new BusinessRuleException("A login already exists for " + employee.getEmail());
        }
        String temporary = PasswordPolicy.temporaryPassword();
        UserAccount account = new UserAccount();
        account.setUsername(employee.getEmail().toLowerCase());
        account.setPasswordHash(encoder.encode(temporary));
        account.setRole(role);
        account.setEmployee(employee);
        account.setMustChangePassword(true);
        accounts.save(account);
        return temporary;
    }

    @Transactional(readOnly = true)
    public List<UserAccount> findAll() {
        return accounts.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public UserAccount get(Long id) {
        return accounts.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    public void changePassword(Long userId, String current, String next, String confirm) {
        UserAccount account = get(userId);
        if (current == null || !encoder.matches(current, account.getPasswordHash())) {
            throw new BusinessRuleException("Your current password is incorrect");
        }
        if (next == null || !next.equals(confirm)) {
            throw new BusinessRuleException("The new passwords do not match");
        }
        String problem = PasswordPolicy.validate(next);
        if (problem != null) {
            throw new BusinessRuleException(problem);
        }
        if (encoder.matches(next, account.getPasswordHash())) {
            throw new BusinessRuleException("Choose a password different from the current one");
        }
        account.setPasswordHash(encoder.encode(next));
        account.setMustChangePassword(false);
        audit.recordAs(account.getUsername(), "PASSWORD_CHANGED", "UserAccount", account.getId(), null);
    }

    /** Called for every wrong password; locks the account at the limit. */
    public void recordFailedLogin(String username) {
        accounts.findByUsernameIgnoreCase(username.trim()).ifPresent(account -> {
            if (account.isLocked()) {
                return;
            }
            account.setFailedAttempts(account.getFailedAttempts() + 1);
            if (account.getFailedAttempts() >= UserAccount.MAX_FAILED_ATTEMPTS) {
                account.setLocked(true);
                audit.recordAs("system", "ACCOUNT_LOCKED", "UserAccount", account.getId(),
                        account.getUsername() + " locked after " + UserAccount.MAX_FAILED_ATTEMPTS + " failed logins");
                notifications.notifyHr("The account " + account.getUsername()
                        + " was locked after too many failed logins", "/admin/users");
            }
        });
    }

    public void recordSuccessfulLogin(String username) {
        accounts.findByUsernameIgnoreCase(username).ifPresent(account -> {
            account.setFailedAttempts(0);
            account.setLastLoginAt(LocalDateTime.now(clock));
        });
    }

    public void unlock(Long id) {
        UserAccount account = get(id);
        if (!account.isLocked()) {
            throw new BusinessRuleException(account.getUsername() + " is not locked");
        }
        account.setLocked(false);
        account.setFailedAttempts(0);
        audit.record("ACCOUNT_UNLOCKED", "UserAccount", id, account.getUsername());
    }

    public void changeRole(Long id, Role role) {
        UserAccount account = get(id);
        if (account.getId().equals(currentUser.principal().map(p -> p.getUserId()).orElse(null))) {
            throw new BusinessRuleException("You cannot change your own role");
        }
        if (account.getRole() == role) {
            return;
        }
        audit.record("ROLE_CHANGED", "UserAccount", id,
                new AuditService.Changes().add("role", account.getRole(), role) + " (" + account.getUsername() + ")");
        account.setRole(role);
    }

    public void setEnabled(Long id, boolean enabled) {
        UserAccount account = get(id);
        if (account.getId().equals(currentUser.principal().map(p -> p.getUserId()).orElse(null))) {
            throw new BusinessRuleException("You cannot disable your own account");
        }
        if (enabled && account.getEmployee() != null && !account.getEmployee().isActive()) {
            throw new BusinessRuleException("This employee has left the company; the login cannot be re-enabled");
        }
        if (account.isEnabled() == enabled) {
            return;
        }
        account.setEnabled(enabled);
        audit.record(enabled ? "ACCOUNT_ENABLED" : "ACCOUNT_DISABLED", "UserAccount", id, account.getUsername());
    }

    /** Issues a new temporary password (also unlocks). Returns it so it can be handed over. */
    public String resetPassword(Long id) {
        UserAccount account = get(id);
        String temporary = PasswordPolicy.temporaryPassword();
        account.setPasswordHash(encoder.encode(temporary));
        account.setMustChangePassword(true);
        account.setLocked(false);
        account.setFailedAttempts(0);
        audit.record("PASSWORD_RESET", "UserAccount", id, account.getUsername());
        return temporary;
    }

    public void disableForExit(Employee employee) {
        accounts.findByEmployeeId(employee.getId()).ifPresent(a -> a.setEnabled(false));
    }

    public void renameForEmployee(Employee employee, String newEmail) {
        accounts.findByEmployeeId(employee.getId()).ifPresent(a -> {
            if (!a.getUsername().equalsIgnoreCase(newEmail) && accounts.existsByUsernameIgnoreCase(newEmail)) {
                throw new BusinessRuleException("Email " + newEmail + " is already used by another login");
            }
            a.setUsername(newEmail.toLowerCase());
        });
    }

    /** First start in a new environment: creates the Super Admin so someone can sign in. */
    public boolean bootstrapSuperAdmin(String username, String password) {
        if (accounts.count() > 0) {
            return false;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username.toLowerCase());
        admin.setPasswordHash(encoder.encode(password));
        admin.setRole(Role.SUPER_ADMIN);
        admin.setMustChangePassword(false);
        accounts.save(admin);
        audit.recordAs("system", "ACCOUNT_CREATED", "UserAccount", admin.getId(), "Bootstrap Super Admin " + username);
        return true;
    }
}
