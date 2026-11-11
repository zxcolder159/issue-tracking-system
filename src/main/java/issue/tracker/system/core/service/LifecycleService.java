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
 * Управление lifecycle: статусы — упорядоченные колонки доски,
 * переходы — рёбра графа workflow.
 *
 * <p>Переходы двунаправленные: одно ребро покрывает перемещения между своими
 * двумя статусами в обе стороны. Статусы удаляются через soft delete
 * (они сохраняют историю своих issues), переходы удаляются окончательно
 * вместе с касающимися их статусами.
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
     * UC-13: возвращает lifecycle проекта со статусами и переходами.
     *
     * @param actor     действующий пользователь
     * @param projectId id проекта
     * @return lifecycle
     * @throws NotFoundException когда у проекта нет lifecycle
     */
    @Transactional(readOnly = true)
    public Lifecycle getLifecycle(User actor, Long projectId) {
        accessService.requireProjectAccess(actor, projectId);
        return requireLifecycle(projectId);
    }

    /**
     * Возвращает неудалённые статусы проекта в порядке на доске; в первый
     * попадают новые issues.
     *
     * @param actor     действующий пользователь
     * @param projectId id проекта
     * @return упорядоченные статусы, пусто, когда у lifecycle их нет
     */
    @Transactional(readOnly = true)
    public List<Status> getStatuses(User actor, Long projectId) {
        return statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(
                getLifecycle(actor, projectId).getId());
    }

    /**
     * UC-14: создаёт статус. Новый статус всегда становится последней колонкой
     * доски; опционально он связывается с указанными статусами переходами,
     * разрешёнными указанным ролям.
     *
     * @param actor            действующий пользователь, должен быть Project Lead или admin
     * @param projectId        id проекта
     * @param name             непустое название нового статуса
     * @param connectToStatusIds id статусов, с которыми создать переходы,
     *                           может быть {@code null} или пустым — тогда переходов нет
     * @param allowedRoles     роли, которым разрешено пользоваться созданными переходами,
     *                         может быть {@code null} для «пока никого»
     * @return сохранённый статус
     * @throws RuleViolationException когда название пустое
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
     * UC-17: добавляет ребро между двумя статусами. Ребро работает в обе
     * стороны; одна и та же пара статусов может быть связана только один раз.
     *
     * @param actor         действующий пользователь, должен быть Project Lead или admin
     * @param projectId     id проекта
     * @param fromStatusId  id первого статуса
     * @param toStatusId    id второго статуса
     * @param allowedRoles  непустое множество ролей, которым разрешено перемещать issues по ребру
     * @return сохранённый переход
     * @throws RuleViolationException когда роли пусты, статусы совпадают
     *         или пара уже связана в любом из направлений
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
     * UC-18: заменяет множество ролей, которым разрешён переход.
     *
     * @param actor         действующий пользователь, должен быть Project Lead или admin
     * @param projectId     id проекта, которому принадлежит переход
     * @param transitionId  id перехода
     * @param allowedRoles  непустое множество новых ролей
     * @return обновлённый переход
     * @throws RuleViolationException когда роли пусты
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
     * UC-19: удаляет ребро. Issues, уже лежащие в этих двух статусах,
     * остаются ровно на месте.
     *
     * @param actor        действующий пользователь, должен быть Project Lead или admin
     * @param projectId    id проекта, которому принадлежит переход
     * @param transitionId id удаляемого перехода
     */
    @Transactional
    public void deleteTransition(User actor, Long projectId, Long transitionId) {
        accessService.requireLead(actor, projectId);
        Transition transition = requireTransitionOfProject(transitionId, projectId);
        transitionRepository.delete(transition);
    }

    /**
     * UC-20: удаляет статус через soft delete вместе с его колонкой доски,
     * переносит его активные issues в выбранный целевой статус и окончательно
     * удаляет каждый переход, касающийся статуса. Когда удалена первая колонка,
     * новые issues автоматически начинают попадать в следующий статус.
     *
     * @param actor             действующий пользователь, должен быть Project Lead или admin
     * @param projectId         id проекта
     * @param statusId          id удаляемого статуса
     * @param migrationTargetId id статуса, в который перенести issues; может быть
     *                          {@code null}, только когда в статусе нет issues
     * @throws RuleViolationException когда это последний оставшийся статус
     *         lifecycle, в статусе есть issues, а цель не указана, или
     *         цель — сам удаляемый статус
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
     * Возвращает ребро между двумя статусами в обоих направлениях, пусто,
     * когда статусы не связаны.
     *
     * @param lifecycleId id lifecycle, которому принадлежат оба статуса
     * @param statusAId   id первого статуса
     * @param statusBId   id второго статуса
     * @return все рёбра, связывающие статусы в любом направлении
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
