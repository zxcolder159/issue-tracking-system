package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Lifecycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Lifecycles — the workflow graphs of projects, exactly one per project.
 */
public interface LifecycleRepository extends JpaRepository<Lifecycle, Long> {

    /**
     * Returns the lifecycle of the project.
     *
     * @param projectId id of the project
     * @return the lifecycle, empty when the project has none
     */
    Optional<Lifecycle> findByProjectId(Long projectId);
}
