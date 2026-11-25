package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Хранилище зарегистрированных аккаунтов. */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Ищет аккаунт по точному значению email, которое служит логином.
     *
     * @param email email аккаунта
     * @return найденный аккаунт или пустой результат
     */
    Optional<User> findByEmail(String email);
}
