package issue.tracker.system.core.service;

import issue.tracker.system.core.exception.ForbiddenOperationException;
import issue.tracker.system.core.model.Issue;
import issue.tracker.system.core.model.Membership;
import issue.tracker.system.core.model.ProjectRole;
import issue.tracker.system.core.model.SystemRole;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Центральное место проверок прав доступа.
 *
 * <p>Права определяются в контексте конкретного проекта, а не глобально:
 * один и тот же пользователь может быть Project Lead в одном проекте
 * и Developer в другом. Системный Admin неявно обладает правами Project Lead
 * в каждом проекте, даже без участия в нём. Project Lead может изменять
 * любую issue, Developer и QA — только созданные ими сами.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final MembershipRepository membershipRepository;

    /**
     * Возвращает активное участие пользователя в проекте.
     *
     * @param actor     действующий пользователь
     * @param projectId id проекта
     * @return участие, в котором хранится роль пользователя в проекте
     * @throws ForbiddenOperationException когда пользователь не активный участник
     *         (участия с установленным {@code deletedAt} игнорируются)
     */
    public Membership requireMember(User actor, Long projectId) {
        return membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(actor.getId(), projectId)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Пользователь не состоит в этом проекте"));
    }

    /**
     * Проверяет, что actor может читать содержимое проекта: любой участник
     * проекта или системный Admin.
     *
     * @param actor     действующий пользователь
     * @param projectId id проекта
     * @throws ForbiddenOperationException когда actor не участник и не admin
     */
    public void requireProjectAccess(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        requireMember(actor, projectId);
    }

    /**
     * Проверяет права Project Lead: либо роль участия, либо системный Admin.
     *
     * @param actor     действующий пользователь
     * @param projectId id проекта
     * @throws ForbiddenOperationException когда actor не admin, а его роль
     *         в проекте отличается от {@code PROJECT_LEAD}
     */
    public void requireLead(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        Membership membership = requireMember(actor, projectId);
        if (membership.getRole() != ProjectRole.PROJECT_LEAD) {
            throw new ForbiddenOperationException(
                    "Действие доступно только Project Lead, текущая роль: " + membership.getRole());
        }
    }

    /**
     * Возвращает эффективную роль actor в проекте; admin всегда действует
     * как Project Lead, даже без участия в проекте.
     *
     * @param actor     действующий пользователь
     * @param projectId id проекта
     * @return роль, на которой основаны права actor
     * @throws ForbiddenOperationException когда actor не участник и не admin
     */
    public ProjectRole requireProjectRole(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return ProjectRole.PROJECT_LEAD;
        }
        return requireMember(actor, projectId).getRole();
    }

    /**
     * Проверяет, что actor может редактировать issue: Project Lead и admin
     * могут изменять любую issue, Developer и QA — только созданные ими.
     *
     * @param actor действующий пользователь
     * @param issue issue, которую нужно изменить
     * @throws ForbiddenOperationException когда actor не участник проекта
     *         этой issue или не имеет прав именно на эту issue
     */
    public void requireCanEditIssue(User actor, Issue issue) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        Membership membership = requireMember(actor, issue.getProject().getId());
        if (membership.getRole() == ProjectRole.PROJECT_LEAD) {
            return;
        }
        if (issue.getAuthor().getUser().getId().equals(actor.getId())) {
            return;
        }
        throw new ForbiddenOperationException(
                "Developer и QA могут изменять только созданные ими issues");
    }
}
