package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Lifecycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LifecycleRepository extends JpaRepository<Lifecycle, Long> {

    Optional<Lifecycle> findByProjectId(Long projectId);
}
