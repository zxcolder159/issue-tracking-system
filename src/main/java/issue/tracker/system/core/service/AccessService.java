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
 * Central place for permission checks.
 *
 * <p>Rights are resolved in the context of a project, never globally: the same
 * user may be Project Lead in one project and Developer in another. The system
 * admin implicitly holds Project Lead rights in every project, even without a
 * membership. Project Lead may change any issue, Developer and QA only the
 * issues they created themselves.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final MembershipRepository membershipRepository;

    /**
     * Returns the active membership of the user in the project.
     *
     * @param actor     the acting user
     * @param projectId id of the project
     * @return the membership carrying the user's project role
     * @throws ForbiddenOperationException when the user is not an active member
     *         (memberships with {@code deletedAt} set are ignored)
     */
    public Membership requireMember(User actor, Long projectId) {
        return membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(actor.getId(), projectId)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Пользователь не состоит в этом проекте"));
    }

    /**
     * Checks that the actor may read project content: any project member or
     * the system admin.
     *
     * @param actor     the acting user
     * @param projectId id of the project
     * @throws ForbiddenOperationException when the actor is neither a member nor the admin
     */
    public void requireProjectAccess(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        requireMember(actor, projectId);
    }

    /**
     * Checks Project Lead rights: either the membership role or the system admin.
     *
     * @param actor     the acting user
     * @param projectId id of the project
     * @throws ForbiddenOperationException when the actor is not the admin and
     *         their project role differs from {@code PROJECT_LEAD}
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
     * Returns the effective role of the actor in the project; the admin always
     * acts as Project Lead, even without a membership.
     *
     * @param actor     the acting user
     * @param projectId id of the project
     * @return the role the actor's permissions are based on
     * @throws ForbiddenOperationException when the actor is a non-member and not the admin
     */
    public ProjectRole requireProjectRole(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return ProjectRole.PROJECT_LEAD;
        }
        return requireMember(actor, projectId).getRole();
    }

    /**
     * Checks that the actor may edit the issue: Project Lead and the admin may
     * edit any issue, Developer and QA only the issues they authored.
     *
     * @param actor the acting user
     * @param issue the issue to be changed
     * @throws ForbiddenOperationException when the actor is not a member of the
     *         issue's project or lacks the rights for this particular issue
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
