package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Transition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Transitions — the edges of the workflow graph. One edge is semantically
 * bidirectional: it allows moves between its statuses in both directions.
 *
 * <p>Transitions are hard-deleted: when a status is removed from the
 * lifecycle, every touching edge disappears with it.
 */
public interface TransitionRepository extends JpaRepository<Transition, Long> {

    /**
     * Returns all transitions of the lifecycle.
     *
     * @param lifecycleId id of the lifecycle
     * @return transitions in no particular order
     */
    List<Transition> findByLifecycleId(Long lifecycleId);

    /**
     * Returns the edges between the two statuses in the given direction.
     *
     * @param lifecycleId id of the lifecycle both statuses belong to
     * @param fromId      id of the source status
     * @param toId        id of the target status
     * @return matching edges, empty when the statuses are not connected this way
     */
    List<Transition> findByLifecycleIdAndFromIdAndToId(Long lifecycleId, Long fromId, Long toId);

    /**
     * Returns all edges leaving the status.
     *
     * @param lifecycleId id of the lifecycle
     * @param fromId      id of the source status
     * @return edges with the status as their source
     */
    List<Transition> findByLifecycleIdAndFromId(Long lifecycleId, Long fromId);

    /**
     * Returns all edges entering the status.
     *
     * @param lifecycleId id of the lifecycle
     * @param toId        id of the target status
     * @return edges with the status as their target
     */
    List<Transition> findByLifecycleIdAndToId(Long lifecycleId, Long toId);

    /**
     * Hard-deletes all edges leaving the status (used when the status is deleted).
     *
     * @param fromId id of the source status
     */
    void deleteByFromId(Long fromId);

    /**
     * Hard-deletes all edges entering the status (used when the status is deleted).
     *
     * @param toId id of the target status
     */
    void deleteByToId(Long toId);
}
