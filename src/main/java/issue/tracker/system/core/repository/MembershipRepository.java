package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Membership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @EntityGraph(attributePaths = {"user", "project"})
    Optional<Membership> findByUserIdAndProjectIdAndDeletedAtNull(Long userId, Long projectId);
}
