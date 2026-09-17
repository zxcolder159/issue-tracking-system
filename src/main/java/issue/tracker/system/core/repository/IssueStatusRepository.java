package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IssueStatusRepository extends JpaRepository<IssueStatus, Long> {
}
