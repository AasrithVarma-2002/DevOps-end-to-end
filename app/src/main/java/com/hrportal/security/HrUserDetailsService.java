package com.hrportal.security;

import com.hrportal.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HrUserDetailsService implements UserDetailsService {

    private final UserAccountRepository accounts;

    public HrUserDetailsService(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return accounts.findByUsernameIgnoreCase(username.trim())
                .map(a -> new HrUserPrincipal(a, a.getEmployee() == null || a.getEmployee().isActive()))
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
    }
}
