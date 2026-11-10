package issue.tracker.system.core.repository;

import issue.tracker.system.core.model.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Issues с запросами, учитывающими soft delete: активные issues идут на доску,
 * в список и в диаграмму Ганта, issues с установленным {@code deletedAt}
 * лежат в корзине.
 */
public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * Возвращает активные (не в корзине) issues проекта для доски, списка
     * и диаграммы Ганта. Каждый фильтр необязателен: {@code null} отключает его.
     *
     * @param projectId      id проекта
     * @param assigneeUserId id пользователя-assignee или {@code null} для любого
     * @param priority       точное значение priority или {@code null} для любого
     * @param type           точное значение type или {@code null} для любого
     * @param titlePattern   паттерн {@code LIKE} в нижнем регистре для заголовка
     *                       (например {@code "%bug%"}), или {@code null} для любого
     * @return подходящие issues, упорядоченные по due date, затем по id
     */
    @Query("""
            select i from Issue i
            where i.project.id = :projectId and i.deletedAt is null
              and (:assigneeUserId is null or i.assignee.user.id = :assigneeUserId)
              and (:priority is null or i.priority = :priority)
              and (:type is null or i.type = :type)
              and (:titlePattern is null or lower(i.title) like lower(:titlePattern))
            order by i.dueDate asc, i.id asc
            """)
    List<Issue> findActive(@Param("projectId") Long projectId,
                           @Param("assigneeUserId") Long assigneeUserId,
                           @Param("priority") String priority,
                           @Param("type") String type,
                           @Param("titlePattern") String titlePattern);

    /**
     * Возвращает все issues проекта, лежащие в корзине. Фильтрацию видимости
     * по роли (Developer и QA видят только свои) выполняет слой сервисов.
     *
     * @param projectId id проекта
     * @return issues с установленным {@code deletedAt}
     */
    List<Issue> findByProjectIdAndDeletedAtNotNull(Long projectId);

    /**
     * Возвращает активные issues, которые сейчас находятся в данном статусе;
     * используется, чтобы вывести их оттуда перед удалением статуса.
     *
     * @param statusId id статуса
     * @return активные issues в статусе
     */
    List<Issue> findByStatusIdAndDeletedAtNull(Long statusId);

    /**
     * Возвращает активную (не в корзине) issue по id.
     *
     * @param id id issue
     * @return issue, пусто, когда она не найдена или лежит в корзине
     */
    Optional<Issue> findByIdAndDeletedAtNull(Long id);
}
