package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Membership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Memberships of users in projects with their per-project roles.
 *
 * <p>Soft-deleted memberships (removed members) are never returned: a removed
 * member loses access to the project immediately, while the issues they
 * created keep pointing at the membership.
 */
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * Returns the active membership of the user in the project.
     *
     * @param userId    id of the user
     * @param projectId id of the project
     * @return the membership with user and project fetched, empty when the user
     *         is not an active member of the project
     */
    @EntityGraph(attributePaths = {"user", "project"})
    Optional<Membership> findByUserIdAndProjectIdAndDeletedAtNull(Long userId, Long projectId);
}
