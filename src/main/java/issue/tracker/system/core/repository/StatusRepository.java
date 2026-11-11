package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Status;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Статусы lifecycle — колонки доски, упорядоченные по их порядку на доске
 * (полю {@code order} в {@link Status}).
 */
public interface StatusRepository extends JpaRepository<Status, Long> {

    /**
     * Возвращает неудалённый статус по id.
     *
     * @param id id статуса
     * @return статус с подгруженным lifecycle, пусто, когда статус не найден
     *         или удалён через soft delete
     */
    @EntityGraph(attributePaths = "lifecycle")
    Optional<Status> findByIdAndDeletedAtNull(Long id);

    /**
     * Возвращает неудалённые статусы lifecycle в порядке на доске
     * (по {@code order}, затем по id). Первый элемент принимает новые issues.
     *
     * @param lifecycleId id lifecycle
     * @return упорядоченные статусы, пусто, когда у lifecycle их нет
     */
    List<Status> findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(Long lifecycleId);
}
