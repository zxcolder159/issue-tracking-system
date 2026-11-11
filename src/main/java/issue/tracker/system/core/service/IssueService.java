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
 * Операции с issues для доски, списка и диаграммы Ганта.
 *
 * <p>Основные правила: новая issue всегда попадает в первый статус lifecycle,
 * а её автор — участие действующего участника; смена статуса должна идти
 * по ребру перехода, разрешённому эффективной роли actor в проекте (рёбра
 * работают в обе стороны); удаление — это soft delete: issue уходит в корзину
 * и может быть восстановлена, пока её исходный статус ещё существует.
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
     * UC-21, UC-22, UC-23, UC-25: возвращает активные (не в корзине) issues
     * проекта с необязательными фильтрами. Один и тот же результат идёт
     * на доску, в список и в диаграмму Ганта.
     *
     * @param actor     действующий пользователь, любой участник проекта или admin
     * @param projectId id проекта
     * @param filter    необязательные фильтры, {@code null} трактуется как «без фильтров»
     * @return подходящие issues, упорядоченные по due date, затем по id
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
     * UC-24: возвращает одну активную issue со всеми её полями.
     *
     * @param actor   действующий пользователь, любой участник проекта или admin
     * @param issueId id issue
     * @return issue
     * @throws NotFoundException когда issue не существует или лежит в корзине
     */
    @Transactional(readOnly = true)
    public Issue getIssue(User actor, Long issueId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireProjectAccess(actor, issue.getProject().getId());
        return issue;
    }

    /**
     * UC-26: создаёт issue в первом статусе lifecycle проекта; автором
     * становится действующий участник.
     *
     * @param actor   действующий пользователь, должен быть активным участником
     *                проекта (в том числе для admin: issue не может существовать
     *                без авторского участия)
     * @param command поля новой issue; title, type, priority и обе даты
     *                обязательны, assignee необязателен
     * @return сохранённая issue
     * @throws ForbiddenOperationException когда actor не активный участник
     * @throws RuleViolationException когда title, type или priority пусты,
     *         due date раньше start date, assignee не участник проекта
     *         или в lifecycle нет статусов
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
     * UC-27: частичное обновление issue. Поля со значением {@code null}
     * остаются без изменений; даты проверяются как комбинация старых
     * и новых значений; смена статуса должна следовать правилам перехода
     * для роли actor.
     *
     * @param actor   действующий пользователь: Project Lead и admin могут
     *                изменять любую issue, Developer и QA — только свою
     * @param issueId id issue
     * @param command поля для изменения, {@code null} означает «оставить текущее значение»
     * @return обновлённая issue
     * @throws RuleViolationException когда передан пустой title, объединённые
     *         даты противоречивы, assignee не участник проекта или смена
     *         статуса нарушает правила переходов
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
     * UC-29: перемещает issue в целевой статус по ребру перехода, разрешённому
     * эффективной роли actor в проекте. Рёбра работают в обе стороны,
     * поэтому перемещение может идти по ребру в любом направлении.
     *
     * @param actor          действующий пользователь: Project Lead и admin могут
     *                       перемещать любую issue, Developer и QA — только свою
     * @param issueId        id issue
     * @param targetStatusId id целевого статуса в том же lifecycle
     * @return обновлённая issue
     * @throws RuleViolationException когда статусы не связаны ребром
     *         или роль actor не может им пользоваться
     */
    @Transactional
    public Issue changeStatus(User actor, Long issueId, Long targetStatusId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        applyTransition(actor, issue, targetStatusId);
        return issue;
    }

    /**
     * UC-30: soft delete — issue исчезает с доски и из списка
     * и попадает в корзину.
     *
     * @param actor   действующий пользователь: Project Lead и admin могут
     *                отправить в корзину любую issue, Developer и QA — только свою
     * @param issueId id issue
     * @return обновлённая issue с установленным {@code deletedAt}
     */
    @Transactional
    public Issue moveToTrash(User actor, Long issueId) {
        Issue issue = requireActiveIssue(issueId);
        accessService.requireCanEditIssue(actor, issue);
        issue.setDeletedAt(Instant.now());
        return issue;
    }

    /**
     * UC-31: issues проекта в корзине. Developer и QA видят только созданные
     * ими issues, Project Lead и admin — все.
     *
     * @param actor     действующий пользователь, любой участник проекта или admin
     * @param projectId id проекта
     * @return issues в корзине, видимые actor
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
     * UC-32: возвращает issue из корзины обратно на доску, в её исходный
     * статус со всеми полями. Когда исходный статус удалён из lifecycle,
     * вместо него нужно передать целевой статус того же lifecycle.
     *
     * @param actor          действующий пользователь: Project Lead и admin могут
     *                       восстановить любую issue, Developer и QA — только свою
     * @param issueId        id issue в корзине
     * @param targetStatusId запасной статус на случай, когда исходного больше
     *                       нет, {@code null} оставляет исходный
     * @return восстановленная issue
     * @throws RuleViolationException когда issue не в корзине, её исходный
     *         статус удалён, а цель не указана, или цель принадлежит
     *         другому lifecycle
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
     * Необязательные фильтры для доски, списка и диаграммы Ганта (UC-22).
     *
     * @param assigneeUserId фильтр по id пользователя-assignee
     * @param priority       фильтр по точному значению priority
     * @param type           фильтр по точному значению type
     * @param titleLike      подстрока заголовка без учёта регистра
     */
    public record Filter(Long assigneeUserId, String priority, String type, String titleLike) {

        /**
         * Фильтр, которому подходит всё.
         *
         * @return фильтр со всеми полями {@code null}
         */
        public static Filter none() {
            return new Filter(null, null, null, null);
        }
    }

    /**
     * Поля новой issue (UC-26).
     *
     * @param projectId      проект, в котором создаётся issue
     * @param title          обязательный заголовок
     * @param description    необязательное описание в свободной форме
     * @param type           обязательное значение type
     * @param priority       обязательное значение priority
     * @param startDate      обязательное начало рабочего окна
     * @param dueDate        обязательный конец рабочего окна, не раньше начала
     * @param assigneeUserId необязательный assignee, должен быть активным участником проекта
     */
    public record CreateCommand(Long projectId, String title, String description, String type,
                                String priority, Instant startDate, Instant dueDate, Long assigneeUserId) {
    }

    /**
     * Частичное обновление issue (UC-27). Каждое поле применяется, только если
     * оно не {@code null}; нетронутые поля сохраняют текущие значения.
     *
     * @param title          новый заголовок
     * @param description    новое описание
     * @param type           новое значение type
     * @param priority       новое значение priority
     * @param startDate      новая дата начала
     * @param dueDate        новая дата окончания
     * @param assigneeUserId новый assignee, активный участник проекта
     * @param targetStatusId новый статус, должен быть достижим по разрешённому переходу
     */
    public record EditCommand(String title, String description, String type, String priority,
                              Instant startDate, Instant dueDate, Long assigneeUserId, Long targetStatusId) {
    }
}
