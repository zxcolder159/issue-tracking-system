package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Statuses of lifecycles — the columns of the board, ordered by their board
 * order (the {@code order} field of {@link Status}).
 */
public interface StatusRepository extends JpaRepository<Status, Long> {

    /**
     * Returns a non-deleted status by id.
     *
     * @param id id of the status
     * @return the status with its lifecycle fetched, empty when not found or soft-deleted
     */
    @EntityGraph(attributePaths = "lifecycle")
    Optional<Status> findByIdAndDeletedAtNull(Long id);

    /**
     * Returns the non-deleted statuses of the lifecycle in board order
     * (by {@code order}, then by id). The first element receives new issues.
     *
     * @param lifecycleId id of the lifecycle
     * @return ordered statuses, empty when the lifecycle has none
     */
    List<Status> findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(Long lifecycleId);
}
