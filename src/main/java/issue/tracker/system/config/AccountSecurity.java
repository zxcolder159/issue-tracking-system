package issue.tracker.system.config;

import issue.tracker.system.core.model.SystemRole;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class AccountSecurity {

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    public static final class AccountPrincipal extends org.springframework.security.core.userdetails.User {

        private final Long userId;
        private final SystemRole systemRole;

        private AccountPrincipal(User account) {
            super(account.getEmail(), account.getPassword(),
                    AuthorityUtils.createAuthorityList("ROLE_" + account.getSystemRole().name()));
            userId = account.getId();
            systemRole = account.getSystemRole();
        }

        public User getActor() {
            return User.builder().id(userId).systemRole(systemRole).build();
        }
    }
}
