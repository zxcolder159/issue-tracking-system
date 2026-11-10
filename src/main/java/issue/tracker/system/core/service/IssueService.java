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
 * Issue operations on the board and in the list: creation always lands in the
 * first lifecycle status, status changes must follow a transition edge allowed
 * to the actor's role, deletion is a soft delete with the trash view.
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

    /** UC-21, UC-22, UC-23, UC-25: active issues of the project with optional filters. */
    @Transactional(readOnly = true)
    public List<Issue> findIssues(User actor, Long projectId, Filter filter) {
        accessService.requireProjectAccess(actor, projectId);
        Filter f = filter == null ? Filter.none() : filter;
        String titlePattern = f.titleLike() == null || f.titleLike().isBlank()
                ? null : "%" + f.titleLike().strip() + "%";
        return issueRepository.findActive(projectId, f.assigneeUserId(), f.priority(), f.type(), titlePattern);
    }

    /** UC-24: a single active issue with all fields. */
    @Transactional(readOnly = true)
    public Issue getIssue(User actor, Long issueId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireProjectAccess(actor, issue.getProject().getId());
        return issue;
    }

    /** UC-26: creates the issue in the first lifecycle status; the actor becomes the author. */
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

    /** UC-27: partial update; a status change must follow the transition rules. */
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

    /** UC-29: moves the issue along an allowed transition edge; edges work in both directions. */
    @Transactional
    public Issue changeStatus(User actor, Long issueId, Long targetStatusId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        applyTransition(actor, issue, targetStatusId);
        return issue;
    }

    /** UC-30: soft delete — the issue goes to the trash. */
    @Transactional
    public Issue moveToTrash(User actor, Long issueId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        issue.setDeletedAt(Instant.now());
        return issue;
    }

    /** UC-31: trashed issues; Developer and QA see only their own, Project Lead and admin all. */
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
     * UC-32: restores the issue into its original status; when that status no
     * longer exists a target status must be provided.
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

    /** Optional filters for board, list and gantt views (UC-22). Null means «no filter». */
    public record Filter(Long assigneeUserId, String priority, String type, String titleLike) {

        public static Filter none() {
            return new Filter(null, null, null, null);
        }
    }

    public record CreateCommand(Long projectId, String title, String description, String type,
                                String priority, Instant startDate, Instant dueDate, Long assigneeUserId) {
    }

    public record EditCommand(String title, String description, String type, String priority,
                              Instant startDate, Instant dueDate, Long assigneeUserId, Long targetStatusId) {
    }
}
