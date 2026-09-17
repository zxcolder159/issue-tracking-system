package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
