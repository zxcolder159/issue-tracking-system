package issue.tracker.system.api.dto;

/**
 * Статус жизненного цикла в карточке задачи и в списке допустимых переходов.
 *
 * @param id id статуса
 * @param name отображаемое название колонки
 * @param order позиция колонки на доске, меньшие значения идут раньше
 * @param projectId id проекта, которому принадлежит статус
 */
public record StatusDto(Long id, String name, Integer order, Long projectId) {
}
