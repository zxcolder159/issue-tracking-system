package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

    Optional<ProjectMember> findByUserIdAndProjectId(Long userId, Long projectId);

}
