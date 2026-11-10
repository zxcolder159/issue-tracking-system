package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StatusRepository extends JpaRepository<Status, Long> {

    @EntityGraph(attributePaths = "lifecycle")
    Optional<Status> findByIdAndDeletedAtNull(Long id);

    List<Status> findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(Long lifecycleId);
}
