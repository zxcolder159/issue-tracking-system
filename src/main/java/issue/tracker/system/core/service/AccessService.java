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
 * Central place for permission checks. Rights are resolved per project:
 * Admin acts as Project Lead in every project even without a membership,
 * Project Lead can do anything with issues, Developer and QA only with their own.
 */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final MembershipRepository membershipRepository;

    /** Returns the active membership of the user or fails; memberships with deletedAt are ignored. */
    public Membership requireMember(User actor, Long projectId) {
        return membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(actor.getId(), projectId)
                .orElseThrow(() -> new ForbiddenOperationException(
                        "Пользователь не состоит в этом проекте"));
    }

    /** Any project member or the system admin can read project content. */
    public void requireProjectAccess(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return;
        }
        requireMember(actor, projectId);
    }

    /** Project Lead rights: the membership role or the system admin. */
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

    /** Effective role of the actor in the project; the admin always acts as Project Lead. */
    public ProjectRole requireProjectRole(User actor, Long projectId) {
        if (actor.getSystemRole() == SystemRole.ADMIN) {
            return ProjectRole.PROJECT_LEAD;
        }
        return requireMember(actor, projectId).getRole();
    }

    /** Developer and QA may edit only issues they authored, Project Lead and admin any issue. */
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
