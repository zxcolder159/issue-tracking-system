package issue.tracker.system.api.dto;

/**
 * Данные пользователя для отображения автора и выбора исполнителя.
 *
 * @param id id пользователя, который клиент передаёт в {@code assigneeUserId} при назначении
 * @param name отображаемое имя пользователя
 */
public record UserSummaryDto(Long id, String name) {
}
