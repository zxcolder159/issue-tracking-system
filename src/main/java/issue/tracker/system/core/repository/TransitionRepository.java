package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Transition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransitionRepository extends JpaRepository<Transition, Long> {

    List<Transition> findByLifecycleId(Long lifecycleId);

    List<Transition> findByLifecycleIdAndFromIdAndToId(Long lifecycleId, Long fromId, Long toId);

    List<Transition> findByLifecycleIdAndFromId(Long lifecycleId, Long fromId);

    List<Transition> findByLifecycleIdAndToId(Long lifecycleId, Long toId);

    void deleteByFromId(Long fromId);

    void deleteByToId(Long toId);
}
