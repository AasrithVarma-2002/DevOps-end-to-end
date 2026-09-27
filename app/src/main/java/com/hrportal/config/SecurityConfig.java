package com.hrportal.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import com.hrportal.repository.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

/**
 * Browser users sign in with a form; API clients use HTTP Basic with the same accounts.
 * Access is by URL area:  /team -> Manager,  /hr -> HR Admin,  /admin -> Super Admin.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserAccountRepository accounts) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Kubernetes probes and the ALB health check call this without credentials
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                        .requestMatchers("/css/**", "/login", "/error").permitAll()
                        .requestMatchers("/team/**", "/api/team/**").hasRole("MANAGER")
                        .requestMatchers("/hr/**", "/api/hr/**").hasRole("HR_ADMIN")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("SUPER_ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler(loginFailureHandler(accounts))
                        .permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .httpBasic(Customizer.withDefaults())
                // API clients authenticate per request with Basic auth and carry no session cookie
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"));
        return http.build();
    }

    /** Each role inherits everything the roles below it can do. */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.fromHierarchy("""
                ROLE_SUPER_ADMIN > ROLE_HR_ADMIN
                ROLE_HR_ADMIN > ROLE_MANAGER
                ROLE_MANAGER > ROLE_EMPLOYEE
                """);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private AuthenticationFailureHandler loginFailureHandler(UserAccountRepository accounts) {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            // the wrong password that hits the limit locks the account (LoginAttemptListener); say so right away
            boolean lockedNow = exception instanceof BadCredentialsException && username != null
                    && accounts.findByUsernameIgnoreCase(username.trim()).map(a -> a.isLocked()).orElse(false);
            String reason = exception instanceof LockedException || lockedNow ? "locked"
                    : exception instanceof DisabledException ? "disabled"
                    : "error";
            response.sendRedirect(request.getContextPath() + "/login?" + reason);
        };
    }
}
