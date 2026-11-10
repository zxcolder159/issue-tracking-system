package issue.tracker.system.core.service;

import issue.tracker.system.core.exception.NotFoundException;
import issue.tracker.system.core.exception.RuleViolationException;
import issue.tracker.system.core.model.Issue;
import issue.tracker.system.core.model.Lifecycle;
import issue.tracker.system.core.model.ProjectRole;
import issue.tracker.system.core.model.Status;
import issue.tracker.system.core.model.Transition;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.IssueRepository;
import issue.tracker.system.core.repository.LifecycleRepository;
import issue.tracker.system.core.repository.StatusRepository;
import issue.tracker.system.core.repository.TransitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lifecycle management: statuses are the ordered columns of the board,
 * transitions are the edges of the workflow graph.
 *
 * <p>Transitions are bidirectional: a single edge covers moves between its two
 * statuses in both directions. Statuses are soft-deleted (they keep their
 * issues' history), transitions are hard-deleted together with the statuses
 * they touch.
 */
@Service
@RequiredArgsConstructor
public class LifecycleService {

    private final LifecycleRepository lifecycleRepository;
    private final StatusRepository statusRepository;
    private final TransitionRepository transitionRepository;
    private final IssueRepository issueRepository;
    private final AccessService accessService;

    /**
     * UC-13: returns the lifecycle of the project with its statuses and transitions.
     *
     * @param actor     the acting user
     * @param projectId id of the project
     * @return the lifecycle
     * @throws NotFoundException when the project has no lifecycle
     */
    @Transactional(readOnly = true)
    public Lifecycle getLifecycle(User actor, Long projectId) {
        accessService.requireProjectAccess(actor, projectId);
        return requireLifecycle(projectId);
    }

    /**
     * Returns the non-deleted statuses of the project in board order; the
     * first one receives newly created issues.
     *
     * @param actor     the acting user
     * @param projectId id of the project
     * @return ordered statuses, empty when the lifecycle has none
     */
    @Transactional(readOnly = true)
    public List<Status> getStatuses(User actor, Long projectId) {
        return statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(
                getLifecycle(actor, projectId).getId());
    }

    /**
     * UC-14: creates a status. The new status always becomes the last column
     * of the board; optionally it is connected to the given statuses with
     * transitions allowed to the given roles.
     *
     * @param actor            the acting user, must be Project Lead or admin
     * @param projectId        id of the project
     * @param name             non-blank name of the new status
     * @param connectToStatusIds ids of statuses to connect with transitions,
     *                           may be {@code null} or empty for none
     * @param allowedRoles     roles allowed to use the created transitions,
     *                         may be {@code null} for «nobody yet»
     * @return the saved status
     * @throws RuleViolationException when the name is blank
     */
    @Transactional
    public Status createStatus(User actor, Long projectId, String name,
                               Set<Long> connectToStatusIds, Set<ProjectRole> allowedRoles) {
        accessService.requireLead(actor, projectId);
        if (name == null || name.isBlank()) {
            throw new RuleViolationException("Название статуса обязательно");
        }
        Lifecycle lifecycle = requireLifecycle(projectId);
        List<Status> statuses = activeStatuses(lifecycle.getId());
        int nextOrder = statuses.stream().mapToInt(Status::getOrder).max().orElse(-1) + 1;
        Status status = statusRepository.save(Status.builder()
                .name(name.strip())
                .order(nextOrder)
                .lifecycle(lifecycle)
                .build());
        for (Long targetId : connectToStatusIds == null ? Set.<Long>of() : connectToStatusIds) {
            if (targetId.equals(status.getId())) {
                continue;
            }
            Status target = requireStatusOfLifecycle(targetId, lifecycle.getId());
            if (findEdge(lifecycle.getId(), status.getId(), target.getId()).isEmpty()) {
                transitionRepository.save(Transition.builder()
                        .lifecycle(lifecycle)
                        .from(status)
                        .to(target)
                        .allowRoles(allowedRoles == null ? Set.of() : new HashSet<>(allowedRoles))
                        .build());
            }
        }
        return status;
    }

    /**
     * UC-17: adds an edge between two statuses. The edge works in both
     * directions; the same pair of statuses may be connected only once.
     *
     * @param actor         the acting user, must be Project Lead or admin
     * @param projectId     id of the project
     * @param fromStatusId  id of the first status
     * @param toStatusId    id of the second status
     * @param allowedRoles  non-empty set of roles allowed to move issues along the edge
     * @return the saved transition
     * @throws RuleViolationException when the roles are empty, the statuses
     *         coincide or the pair is already connected in either direction
     */
    @Transactional
    public Transition addTransition(User actor, Long projectId, Long fromStatusId, Long toStatusId,
                                    Set<ProjectRole> allowedRoles) {
        accessService.requireLead(actor, projectId);
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            throw new RuleViolationException("Выберите хотя бы одну роль, которой разрешён переход");
        }
        if (fromStatusId.equals(toStatusId)) {
            throw new RuleViolationException("Переход статуса в самого себя не имеет смысла");
        }
        Lifecycle lifecycle = requireLifecycle(projectId);
        Status from = requireStatusOfLifecycle(fromStatusId, lifecycle.getId());
        Status to = requireStatusOfLifecycle(toStatusId, lifecycle.getId());
        if (!findEdge(lifecycle.getId(), from.getId(), to.getId()).isEmpty()) {
            throw new RuleViolationException(
                    "Переход между статусами «%s» и «%s» уже существует".formatted(from.getName(), to.getName()));
        }
        return transitionRepository.save(Transition.builder()
                .lifecycle(lifecycle)
                .from(from)
                .to(to)
                .allowRoles(new HashSet<>(allowedRoles))
                .build());
    }

    /**
     * UC-18: replaces the set of roles allowed to use the transition.
     *
     * @param actor         the acting user, must be Project Lead or admin
     * @param projectId     id of the project the transition belongs to
     * @param transitionId  id of the transition
     * @param allowedRoles  non-empty set of new roles
     * @return the updated transition
     * @throws RuleViolationException when the roles are empty
     */
    @Transactional
    public Transition updateTransitionRoles(User actor, Long projectId, Long transitionId,
                                            Set<ProjectRole> allowedRoles) {
        accessService.requireLead(actor, projectId);
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            throw new RuleViolationException("Выберите хотя бы одну роль, которой разрешён переход");
        }
        Transition transition = requireTransitionOfProject(transitionId, projectId);
        transition.setAllowRoles(new HashSet<>(allowedRoles));
        return transition;
    }

    /**
     * UC-19: removes the edge. Issues already sitting in the two statuses stay
     * exactly where they are.
     *
     * @param actor        the acting user, must be Project Lead or admin
     * @param projectId    id of the project the transition belongs to
     * @param transitionId id of the transition to remove
     */
    @Transactional
    public void deleteTransition(User actor, Long projectId, Long transitionId) {
        accessService.requireLead(actor, projectId);
        Transition transition = requireTransitionOfProject(transitionId, projectId);
        transitionRepository.delete(transition);
    }

    /**
     * UC-20: soft-deletes the status together with its board column, moves its
     * active issues to the chosen migration target and hard-deletes every
     * transition touching the status. When the first column is deleted, new
     * issues start landing in the next status automatically.
     *
     * @param actor             the acting user, must be Project Lead or admin
     * @param projectId         id of the project
     * @param statusId          id of the status to delete
     * @param migrationTargetId id of the status to move the issues to; may be
     *                          {@code null} only when the status holds no issues
     * @throws RuleViolationException when this is the last remaining status of
     *         the lifecycle, the status holds issues but no target is given, or
     *         the target is the deleted status itself
     */
    @Transactional
    public void deleteStatus(User actor, Long projectId, Long statusId, Long migrationTargetId) {
        accessService.requireLead(actor, projectId);
        Lifecycle lifecycle = requireLifecycle(projectId);
        Status status = requireStatusOfLifecycle(statusId, lifecycle.getId());
        List<Status> statuses = activeStatuses(lifecycle.getId());
        if (statuses.size() <= 1) {
            throw new RuleViolationException("Нельзя удалить единственный статус lifecycle");
        }
        List<Issue> affected = issueRepository.findByStatusIdAndDeletedAtNull(status.getId());
        if (!affected.isEmpty()) {
            if (migrationTargetId == null) {
                throw new RuleViolationException(
                        "В статусе «%s» %d issues — выберите статус для переноса"
                                .formatted(status.getName(), affected.size()));
            }
            if (migrationTargetId.equals(status.getId())) {
                throw new RuleViolationException("Нельзя переносить issues в удаляемый статус");
            }
            Status target = requireStatusOfLifecycle(migrationTargetId, lifecycle.getId());
            affected.forEach(issue -> issue.setStatus(target));
        }
        transitionRepository.deleteByFromId(status.getId());
        transitionRepository.deleteByToId(status.getId());
        status.setDeletedAt(Instant.now());
    }

    /**
     * Returns the edge between the two statuses in both directions, empty when
     * the statuses are not connected.
     *
     * @param lifecycleId id of the lifecycle both statuses belong to
     * @param statusAId   id of the first status
     * @param statusBId   id of the second status
     * @return all edges connecting the statuses either way
     */
    List<Transition> findEdge(Long lifecycleId, Long statusAId, Long statusBId) {
        List<Transition> edge = new ArrayList<>(
                transitionRepository.findByLifecycleIdAndFromIdAndToId(lifecycleId, statusAId, statusBId));
        edge.addAll(transitionRepository.findByLifecycleIdAndFromIdAndToId(lifecycleId, statusBId, statusAId));
        return edge;
    }

    private Lifecycle requireLifecycle(Long projectId) {
        return lifecycleRepository.findByProjectId(projectId)
                .orElseThrow(() -> new NotFoundException("Lifecycle проекта %d не найден".formatted(projectId)));
    }

    private List<Status> activeStatuses(Long lifecycleId) {
        return statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(lifecycleId);
    }

    private Status requireStatusOfLifecycle(Long statusId, Long lifecycleId) {
        Status status = statusRepository.findByIdAndDeletedAtNull(statusId)
                .orElseThrow(() -> new NotFoundException("Статус %d не найден".formatted(statusId)));
        if (!status.getLifecycle().getId().equals(lifecycleId)) {
            throw new NotFoundException("Статус %d не найден в этом lifecycle".formatted(statusId));
        }
        return status;
    }

    private Transition requireTransitionOfProject(Long transitionId, Long projectId) {
        Transition transition = transitionRepository.findById(transitionId)
                .orElseThrow(() -> new NotFoundException("Переход %d не найден".formatted(transitionId)));
        if (!transition.getLifecycle().getProject().getId().equals(projectId)) {
            throw new NotFoundException("Переход %d не найден в этом проекте".formatted(transitionId));
        }
        return transition;
    }
}
