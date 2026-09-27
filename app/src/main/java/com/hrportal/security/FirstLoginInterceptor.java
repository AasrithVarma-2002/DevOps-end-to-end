package com.hrportal.security;

import com.hrportal.domain.UserAccount;
import com.hrportal.repository.UserAccountRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Enforces the first-login journey for browser users:
 * 1. change the temporary password, then 2. complete the profile.
 */
@Component
public class FirstLoginInterceptor implements HandlerInterceptor {

    private final CurrentUser currentUser;
    private final UserAccountRepository accounts;

    public FirstLoginInterceptor(CurrentUser currentUser, UserAccountRepository accounts) {
        this.currentUser = currentUser;
        this.accounts = accounts;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        var principal = currentUser.principal();
        if (principal.isEmpty()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        UserAccount account = accounts.findById(principal.get().getUserId()).orElse(null);
        if (account == null) {
            return true;
        }
        if (account.isMustChangePassword() && !path.startsWith("/account/password")) {
            response.sendRedirect(request.getContextPath() + "/account/password");
            return false;
        }
        if (!account.isMustChangePassword() && account.getEmployee() != null
                && !account.getEmployee().isProfileCompleted() && !path.startsWith("/profile")
                && !path.startsWith("/account/password")) {
            response.sendRedirect(request.getContextPath() + "/profile/edit");
            return false;
        }
        return true;
    }
}
