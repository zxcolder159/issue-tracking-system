package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IssueRepository extends JpaRepository<Issue, Long> {

    @Query("""
            select i from Issue i
            where i.project.id = :projectId and i.deletedAt is null
              and (:assigneeUserId is null or i.assignee.user.id = :assigneeUserId)
              and (:priority is null or i.priority = :priority)
              and (:type is null or i.type = :type)
              and (:titlePattern is null or lower(i.title) like lower(:titlePattern))
            order by i.dueDate asc, i.id asc
            """)
    List<Issue> findActive(@Param("projectId") Long projectId,
                           @Param("assigneeUserId") Long assigneeUserId,
                           @Param("priority") String priority,
                           @Param("type") String type,
                           @Param("titlePattern") String titlePattern);

    List<Issue> findByProjectIdAndDeletedAtNotNull(Long projectId);

    List<Issue> findByStatusIdAndDeletedAtNull(Long statusId);

    Optional<Issue> findByIdAndDeletedAtNull(Long id);
}
