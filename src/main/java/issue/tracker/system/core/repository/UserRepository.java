package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

}
