package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Issues with soft-delete aware queries: active issues feed the board, the
 * list and the gantt view, issues with {@code deletedAt} set live in the trash.
 */
public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * Returns active (not trashed) issues of the project for the board, list
     * and gantt views. Every filter is optional: {@code null} disables it.
     *
     * @param projectId      id of the project
     * @param assigneeUserId id of the assignee's user, or {@code null} for any
     * @param priority       exact priority value, or {@code null} for any
     * @param type           exact type value, or {@code null} for any
     * @param titlePattern   lowercased {@code LIKE} pattern for the title
     *                       (e.g. {@code "%bug%"}), or {@code null} for any
     * @return matching issues ordered by due date, then by id
     */
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

    /**
     * Returns all trashed issues of the project. Visibility filtering by role
     * (Developer and QA see only their own) is applied by the service layer.
     *
     * @param projectId id of the project
     * @return issues with {@code deletedAt} set
     */
    List<Issue> findByProjectIdAndDeletedAtNotNull(Long projectId);

    /**
     * Returns the active issues currently in the given status; used to move
     * them away before the status is deleted.
     *
     * @param statusId id of the status
     * @return active issues in the status
     */
    List<Issue> findByStatusIdAndDeletedAtNull(Long statusId);

    /**
     * Returns an active (not trashed) issue by id.
     *
     * @param id id of the issue
     * @return the issue, empty when not found or in the trash
     */
    Optional<Issue> findByIdAndDeletedAtNull(Long id);
}
