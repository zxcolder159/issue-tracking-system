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
 * Lifecycle management: statuses as ordered board columns and transitions as
 * edges of the workflow graph. Transitions are bidirectional: one edge covers
 * moves in both directions.
 */
@Service
@RequiredArgsConstructor
public class LifecycleService {

    private final LifecycleRepository lifecycleRepository;
    private final StatusRepository statusRepository;
    private final TransitionRepository transitionRepository;
    private final IssueRepository issueRepository;
    private final AccessService accessService;

    @Transactional(readOnly = true)
    public Lifecycle getLifecycle(User actor, Long projectId) {
        accessService.requireProjectAccess(actor, projectId);
        return requireLifecycle(projectId);
    }

    /** Ordered non-deleted statuses of the project; the first one receives new issues. */
    @Transactional(readOnly = true)
    public List<Status> getStatuses(User actor, Long projectId) {
        return statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(
                getLifecycle(actor, projectId).getId());
    }

    /** UC-14: new status becomes the last board column, optionally connected to given statuses. */
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

    /** UC-17: adds an edge between two statuses; the same edge in either direction is a duplicate. */
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

    /** UC-18: replaces the set of roles allowed to use the transition. */
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

    /** UC-19: removes the edge; issues already in the statuses stay where they are. */
    @Transactional
    public void deleteTransition(User actor, Long projectId, Long transitionId) {
        accessService.requireLead(actor, projectId);
        Transition transition = requireTransitionOfProject(transitionId, projectId);
        transitionRepository.delete(transition);
    }

    /**
     * UC-20: soft-deletes the status, moves its issues to the chosen status and
     * removes every transition touching the deleted status. The last remaining
     * status cannot be deleted.
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

    /** Both directions of the edge between the statuses, empty when the statuses are not connected. */
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
