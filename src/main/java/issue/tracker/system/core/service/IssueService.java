package issue.tracker.system.core.service;

import issue.tracker.system.core.exception.ForbiddenOperationException;
import issue.tracker.system.core.exception.NotFoundException;
import issue.tracker.system.core.exception.RuleViolationException;
import issue.tracker.system.core.model.Issue;
import issue.tracker.system.core.model.Lifecycle;
import issue.tracker.system.core.model.Membership;
import issue.tracker.system.core.model.ProjectRole;
import issue.tracker.system.core.model.Status;
import issue.tracker.system.core.model.SystemRole;
import issue.tracker.system.core.model.Transition;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.IssueRepository;
import issue.tracker.system.core.repository.LifecycleRepository;
import issue.tracker.system.core.repository.MembershipRepository;
import issue.tracker.system.core.repository.StatusRepository;
import issue.tracker.system.core.repository.TransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Issue operations for the board, the list and the gantt view.
 *
 * <p>Core rules: a new issue always lands in the first lifecycle status and
 * its author is the acting member's membership; a status change must follow a
 * transition edge allowed to the actor's effective project role (edges work in
 * both directions); deletion is a soft delete — the issue goes to the trash
 * and can be restored while its original status still exists.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueRepository issueRepository;
    private final LifecycleRepository lifecycleRepository;
    private final StatusRepository statusRepository;
    private final TransitionRepository transitionRepository;
    private final MembershipRepository membershipRepository;
    private final AccessService accessService;

    /**
     * UC-21, UC-22, UC-23, UC-25: returns the active (not trashed) issues of
     * the project with optional filters. The same result feeds the board, the
     * list and the gantt view.
     *
     * @param actor     the acting user, any project member or the admin
     * @param projectId id of the project
     * @param filter    optional filters, {@code null} is treated as «no filters»
     * @return matching issues ordered by due date, then by id
     */
    @Transactional(readOnly = true)
    public List<Issue> findIssues(User actor, Long projectId, Filter filter) {
        accessService.requireProjectAccess(actor, projectId);
        Filter f = filter == null ? Filter.none() : filter;
        String titlePattern = f.titleLike() == null || f.titleLike().isBlank()
                ? null : "%" + f.titleLike().strip() + "%";
        return issueRepository.findActive(projectId, f.assigneeUserId(), f.priority(), f.type(), titlePattern);
    }

    /**
     * UC-24: returns a single active issue with all its fields.
     *
     * @param actor   the acting user, any project member or the admin
     * @param issueId id of the issue
     * @return the issue
     * @throws NotFoundException when the issue does not exist or is in the trash
     */
    @Transactional(readOnly = true)
    public Issue getIssue(User actor, Long issueId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireProjectAccess(actor, issue.getProject().getId());
        return issue;
    }

    /**
     * UC-26: creates the issue in the first status of the project lifecycle;
     * the acting member becomes the author.
     *
     * @param actor   the acting user, must be an active project member (also
     *                for the admin: an issue cannot exist without an author
     *                membership)
     * @param command fields of the new issue; title, type, priority and both
     *                dates are required, assignee is optional
     * @return the saved issue
     * @throws ForbiddenOperationException when the actor is not an active member
     * @throws RuleViolationException when the title, type or priority is blank,
     *         the due date precedes the start date, the assignee is not a
     *         project member or the lifecycle has no statuses
     */
    @Transactional
    public Issue create(User actor, CreateCommand command) {
        Membership author = membershipRepository
                .findByUserIdAndProjectIdAndDeletedAtNull(actor.getId(), command.projectId())
                .orElseThrow(() -> new ForbiddenOperationException(actor.getSystemRole() == SystemRole.ADMIN
                        ? "У issue обязателен автор: администратор должен состоять в проекте, чтобы создать issue"
                        : "Пользователь не состоит в этом проекте"));
        validateDates(command.startDate(), command.dueDate());
        if (command.title() == null || command.title().isBlank()) {
            throw new RuleViolationException("Название issue обязательно");
        }
        if (isBlank(command.type()) || isBlank(command.priority())) {
            throw new RuleViolationException("Type и Priority обязательны");
        }
        Membership assignee = command.assigneeUserId() == null ? null
                : requireActiveMembership(command.assigneeUserId(), command.projectId());
        List<Status> statuses = statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(
                requireLifecycle(command.projectId()).getId());
        if (statuses.isEmpty()) {
            throw new RuleViolationException("В проекте нет ни одного статуса — сначала настройте lifecycle");
        }
        return issueRepository.save(Issue.builder()
                .title(command.title().strip())
                .description(command.description())
                .type(command.type().strip())
                .priority(command.priority().strip())
                .startDate(command.startDate())
                .dueDate(command.dueDate())
                .author(author)
                .assignee(assignee)
                .project(author.getProject())
                .status(statuses.getFirst())
                .build());
    }

    /**
     * UC-27: partial update of the issue. {@code null} fields are left
     * unchanged; dates are validated as a combination of old and new values;
     * a status change must follow the transition rules for the actor's role.
     *
     * @param actor   the acting user: Project Lead and admin may edit any
     *                issue, Developer and QA only their own
     * @param issueId id of the issue
     * @param command fields to change, {@code null} means «keep the current value»
     * @return the updated issue
     * @throws RuleViolationException when a blank title is passed, the merged
     *         dates are inconsistent, the assignee is not a project member or
     *         the status change violates the transition rules
     */
    @Transactional
    public Issue edit(User actor, Long issueId, EditCommand command) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        if (command.title() != null) {
            if (command.title().isBlank()) {
                throw new RuleViolationException("Название issue не может быть пустым");
            }
            issue.setTitle(command.title().strip());
        }
        if (command.description() != null) {
            issue.setDescription(command.description());
        }
        if (!isBlank(command.type())) {
            issue.setType(command.type().strip());
        }
        if (!isBlank(command.priority())) {
            issue.setPriority(command.priority().strip());
        }
        Instant startDate = command.startDate() != null ? command.startDate() : issue.getStartDate();
        Instant dueDate = command.dueDate() != null ? command.dueDate() : issue.getDueDate();
        validateDates(startDate, dueDate);
        issue.setStartDate(startDate);
        issue.setDueDate(dueDate);
        if (command.assigneeUserId() != null) {
            issue.setAssignee(requireActiveMembership(command.assigneeUserId(), issue.getProject().getId()));
        }
        if (command.targetStatusId() != null) {
            applyTransition(actor, issue, command.targetStatusId());
        }
        return issue;
    }

    /**
     * UC-29: moves the issue to the target status along a transition edge
     * allowed to the actor's effective project role. Edges work in both
     * directions, so the move may follow the edge either way.
     *
     * @param actor          the acting user: Project Lead and admin may move
     *                       any issue, Developer and QA only their own
     * @param issueId        id of the issue
     * @param targetStatusId id of the target status within the same lifecycle
     * @return the updated issue
     * @throws RuleViolationException when the statuses are not connected by an
     *         edge or the actor's role is not allowed to use it
     */
    @Transactional
    public Issue changeStatus(User actor, Long issueId, Long targetStatusId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        applyTransition(actor, issue, targetStatusId);
        return issue;
    }

    /**
     * UC-30: soft delete — the issue disappears from the board and the list
     * and goes to the trash.
     *
     * @param actor   the acting user: Project Lead and admin may trash any
     *                issue, Developer and QA only their own
     * @param issueId id of the issue
     * @return the updated issue with {@code deletedAt} set
     */
    @Transactional
    public Issue moveToTrash(User actor, Long issueId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        issue.setDeletedAt(Instant.now());
        return issue;
    }

    /**
     * UC-31: trashed issues of the project. Developer and QA see only the
     * issues they created themselves, Project Lead and the admin see all.
     *
     * @param actor     the acting user, any project member or the admin
     * @param projectId id of the project
     * @return trashed issues visible to the actor
     */
    @Transactional(readOnly = true)
    public List<Issue> trash(User actor, Long projectId) {
        ProjectRole role = accessService.requireProjectRole(actor, projectId);
        List<Issue> trashed = issueRepository.findByProjectIdAndDeletedAtNotNull(projectId);
        if (role == ProjectRole.PROJECT_LEAD) {
            return trashed;
        }
        return trashed.stream()
                .filter(issue -> issue.getAuthor().getUser().getId().equals(actor.getId()))
                .toList();
    }

    /**
     * UC-32: restores the issue from the trash back onto the board, into its
     * original status with all fields intact. When the original status has
     * been deleted from the lifecycle, a target status of the same lifecycle
     * must be provided instead.
     *
     * @param actor          the acting user: Project Lead and admin may restore
     *                       any issue, Developer and QA only their own
     * @param issueId        id of the trashed issue
     * @param targetStatusId fallback status for the case when the original one
     *                       no longer exists, {@code null} keeps the original
     * @return the restored issue
     * @throws RuleViolationException when the issue is not in the trash, its
     *         original status is gone but no target is given, or the target
     *         belongs to another lifecycle
     */
    @Transactional
    public Issue restore(User actor, Long issueId, Long targetStatusId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new NotFoundException("Issue %d не найдена".formatted(issueId)));
        if (issue.getDeletedAt() == null) {
            throw new RuleViolationException("Issue не находится в корзине");
        }
        accessService.requireCanEditIssue(actor, issue);
        if (issue.getStatus().getDeletedAt() != null) {
            if (targetStatusId == null) {
                throw new RuleViolationException(
                        "Исходный статус «%s» удалён из lifecycle — выберите статус для восстановления"
                                .formatted(issue.getStatus().getName()));
            }
            Status target = statusRepository.findByIdAndDeletedAtNull(targetStatusId)
                    .orElseThrow(() -> new NotFoundException("Статус %d не найден".formatted(targetStatusId)));
            if (!target.getLifecycle().getId().equals(issue.getStatus().getLifecycle().getId())) {
                throw new RuleViolationException("Статус восстановления не принадлежит lifecycle этого проекта");
            }
            issue.setStatus(target);
        }
        issue.setDeletedAt(null);
        return issue;
    }

    private void applyTransition(User actor, Issue issue, Long targetStatusId) {
        if (targetStatusId.equals(issue.getStatus().getId())) {
            return;
        }
        Status from = issue.getStatus();
        Status target = statusRepository.findByIdAndDeletedAtNull(targetStatusId)
                .orElseThrow(() -> new NotFoundException("Статус %d не найден".formatted(targetStatusId)));
        if (!target.getLifecycle().getId().equals(from.getLifecycle().getId())) {
            throw new RuleViolationException("Целевой статус не принадлежит lifecycle этого проекта");
        }
        List<Transition> edge = transitionRepository
                .findByLifecycleIdAndFromIdAndToId(target.getLifecycle().getId(), from.getId(), target.getId());
        if (edge.isEmpty()) {
            edge = transitionRepository.findByLifecycleIdAndFromIdAndToId(
                    target.getLifecycle().getId(), target.getId(), from.getId());
        }
        if (edge.isEmpty()) {
            throw new RuleViolationException(
                    "Переход из «%s» в «%s» не разрешён lifecycle".formatted(from.getName(), target.getName()));
        }
        ProjectRole role = accessService.requireProjectRole(actor, issue.getProject().getId());
        if (!edge.getFirst().getAllowRoles().contains(role)) {
            throw new RuleViolationException(
                    "Роли %s не разрешён переход «%s» → «%s»"
                            .formatted(role, from.getName(), target.getName()));
        }
        issue.setStatus(target);
    }

    private Issue requireActiveIssue(Long issueId) {
        return issueRepository.findByIdAndDeletedAtNull(issueId)
                .orElseThrow(() -> new NotFoundException("Issue %d не найдена".formatted(issueId)));
    }

    private Membership requireActiveMembership(Long userId, Long projectId) {
        return membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(userId, projectId)
                .orElseThrow(() -> new RuleViolationException(
                        "Пользователь %d не участник проекта — назначить его assignee нельзя".formatted(userId)));
    }

    private Lifecycle requireLifecycle(Long projectId) {
        return lifecycleRepository.findByProjectId(projectId)
                .orElseThrow(() -> new NotFoundException("Lifecycle проекта %d не найден".formatted(projectId)));
    }

    private void validateDates(Instant startDate, Instant dueDate) {
        if (startDate == null || dueDate == null) {
            throw new RuleViolationException("Start date и Due date обязательны");
        }
        if (dueDate.isBefore(startDate)) {
            throw new RuleViolationException("Дата окончания не может быть раньше даты начала");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Optional filters for the board, list and gantt views (UC-22).
     *
     * @param assigneeUserId filter by the assignee's user id
     * @param priority       filter by the exact priority value
     * @param type           filter by the exact type value
     * @param titleLike      case-insensitive substring of the title
     */
    public record Filter(Long assigneeUserId, String priority, String type, String titleLike) {

        /**
         * A filter that matches everything.
         *
         * @return a filter with all fields set to {@code null}
         */
        public static Filter none() {
            return new Filter(null, null, null, null);
        }
    }

    /**
     * Fields of a new issue (UC-26).
     *
     * @param projectId      project to create the issue in
     * @param title          required title
     * @param description    optional free-form description
     * @param type           required type value
     * @param priority       required priority value
     * @param startDate      required start of the work window
     * @param dueDate        required end of the work window, must not precede the start
     * @param assigneeUserId optional assignee, must be an active project member
     */
    public record CreateCommand(Long projectId, String title, String description, String type,
                                String priority, Instant startDate, Instant dueDate, Long assigneeUserId) {
    }

    /**
     * Partial update of an issue (UC-27). Every field is applied only when it
     * is not {@code null}; untouched fields keep their current values.
     *
     * @param title          new title
     * @param description    new description
     * @param type           new type value
     * @param priority       new priority value
     * @param startDate      new start date
     * @param dueDate        new due date
     * @param assigneeUserId new assignee, an active project member
     * @param targetStatusId new status, must be reachable along an allowed transition
     */
    public record EditCommand(String title, String description, String type, String priority,
                              Instant startDate, Instant dueDate, Long assigneeUserId, Long targetStatusId) {
    }
}
