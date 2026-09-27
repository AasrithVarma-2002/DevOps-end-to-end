package com.hrportal.security;

import com.hrportal.service.UserAccountService;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/** Counts wrong passwords and locks the account after {@code UserAccount.MAX_FAILED_ATTEMPTS}. */
@Component
public class LoginAttemptListener {

    private final UserAccountService accounts;

    public LoginAttemptListener(UserAccountService accounts) {
        this.accounts = accounts;
    }

    @EventListener
    public void onFailure(AuthenticationFailureBadCredentialsEvent event) {
        accounts.recordFailedLogin(String.valueOf(event.getAuthentication().getPrincipal()));
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        accounts.recordSuccessfulLogin(event.getAuthentication().getName());
    }
}
