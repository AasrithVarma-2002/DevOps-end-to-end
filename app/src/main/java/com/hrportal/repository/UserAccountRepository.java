package com.hrportal.repository;

import com.hrportal.domain.Role;
import com.hrportal.domain.UserAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    Optional<UserAccount> findByEmployeeId(Long employeeId);

    boolean existsByUsernameIgnoreCase(String username);

    List<UserAccount> findByRoleInAndEnabledTrue(List<Role> roles);

    List<UserAccount> findAllByOrderByUsernameAsc();
}
