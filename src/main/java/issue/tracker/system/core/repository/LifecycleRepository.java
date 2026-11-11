package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Lifecycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Lifecycle — графы workflow проектов, ровно один на проект.
 */
public interface LifecycleRepository extends JpaRepository<Lifecycle, Long> {

    /**
     * Возвращает lifecycle проекта.
     *
     * @param projectId id проекта
     * @return lifecycle, пусто, когда у проекта его нет
     */
    Optional<Lifecycle> findByProjectId(Long projectId);
}
