package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Transition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Переходы — рёбра графа workflow. Одно ребро семантически двунаправленное:
 * оно разрешает перемещения между своими статусами в обе стороны.
 *
 * <p>Переходы удаляются окончательно (hard delete): когда статус удаляют
 * из lifecycle, каждое касающееся его ребро исчезает вместе с ним.
 */
public interface TransitionRepository extends JpaRepository<Transition, Long> {

    /**
     * Возвращает все переходы lifecycle.
     *
     * @param lifecycleId id lifecycle
     * @return переходы без определённого порядка
     */
    List<Transition> findByLifecycleId(Long lifecycleId);

    /**
     * Возвращает рёбра между двумя статусами в заданном направлении.
     *
     * @param lifecycleId id lifecycle, которому принадлежат оба статуса
     * @param fromId      id исходного статуса
     * @param toId        id целевого статуса
     * @return подходящие рёбра, пусто, когда статусы так не связаны
     */
    List<Transition> findByLifecycleIdAndFromIdAndToId(Long lifecycleId, Long fromId, Long toId);

    /**
     * Возвращает все рёбра, исходящие из статуса.
     *
     * @param lifecycleId id lifecycle
     * @param fromId      id исходного статуса
     * @return рёбра, у которых этот статус исходный
     */
    List<Transition> findByLifecycleIdAndFromId(Long lifecycleId, Long fromId);

    /**
     * Возвращает все рёбра, входящие в статус.
     *
     * @param lifecycleId id lifecycle
     * @param toId        id целевого статуса
     * @return рёбра, у которых этот статус целевой
     */
    List<Transition> findByLifecycleIdAndToId(Long lifecycleId, Long toId);

    /**
     * Окончательно удаляет все рёбра, исходящие из статуса
     * (используется при удалении статуса).
     *
     * @param fromId id исходного статуса
     */
    void deleteByFromId(Long fromId);

    /**
     * Окончательно удаляет все рёбра, входящие в статус
     * (используется при удалении статуса).
     *
     * @param toId id целевого статуса
     */
    void deleteByToId(Long toId);
}
