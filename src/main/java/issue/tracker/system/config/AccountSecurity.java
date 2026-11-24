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

/** Подключает аккаунты из базы данных к Spring Security через вход по email и проверку BCrypt-хеша. */
@Configuration(proxyBeanMethods = false)
public class AccountSecurity {

    /**
     * Загружает данные аккаунта по email для проверки учётных данных.
     *
     * @param users репозиторий аккаунтов
     * @return загрузчик аккаунтов для Spring Security
     */
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .map(AccountPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
    }

    /**
     * Задаёт формат хешей паролей аккаунтов.
     *
     * @return кодировщик BCrypt
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Представляет аккаунт в Spring Security и хранит id и системную роль для проверок доступа. */
    public static final class AccountPrincipal extends org.springframework.security.core.userdetails.User {

        /** Идентификатор аккаунта для проверки участия и авторства задач. */
        private final Long userId;
        /** Системная роль аккаунта для проверки административных прав. */
        private final SystemRole systemRole;

        private AccountPrincipal(User account) {
            super(account.getEmail(), account.getPassword(),
                    AuthorityUtils.createAuthorityList("ROLE_" + account.getSystemRole().name()));
            userId = account.getId();
            systemRole = account.getSystemRole();
        }

        /**
         * Предоставляет контроллеру данные действующего пользователя из контекста безопасности.
         *
         * @return пользователь с заполненными id и системной ролью
         */
        public User getActor() {
            return User.builder().id(userId).systemRole(systemRole).build();
        }
    }
}
