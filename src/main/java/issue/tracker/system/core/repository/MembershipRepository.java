package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Membership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Участия пользователей в проектах с их ролями внутри каждого проекта.
 *
 * <p>Участия в состоянии soft delete (исключённые участники) никогда
 * не возвращаются: исключённый участник теряет доступ к проекту сразу,
 * а созданные им issues продолжают ссылаться на это участие.
 */
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * Возвращает активное участие пользователя в проекте.
     *
     * @param userId    id пользователя
     * @param projectId id проекта
     * @return участие с подгруженными user и project, пусто, когда пользователь
     *         не является активным участником проекта
     */
    @EntityGraph(attributePaths = {"user", "project"})
    Optional<Membership> findByUserIdAndProjectIdAndDeletedAtNull(Long userId, Long projectId);
}
