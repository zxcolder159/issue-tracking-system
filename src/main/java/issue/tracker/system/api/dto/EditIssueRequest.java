package issue.tracker.system.api.dto;

import java.time.Instant;

/**
 * Тело запроса на частичное обновление задачи. Сервис применяет переданные
 * значения, отличные от {@code null}. Если указана одна дата, сервис проверяет
 * её вместе с текущим значением второй даты.
 *
 * @param title новое название, при передаче должно содержать непробельные символы
 * @param description новое описание, пустая строка очищает текст
 * @param type новый тип, применяется при непустом значении
 * @param priority новый приоритет, применяется при непустом значении
 * @param startDate новая дата начала работ
 * @param dueDate новая дата окончания, равная дате начала или более поздняя
 * @param assigneeUserId id пользователя, которого нужно назначить из активных участников проекта
 * @param targetStatusId id целевого статуса для разрешённого роли пользователя перехода
 */
public record EditIssueRequest(String title, String description, String type, String priority,
                               Instant startDate, Instant dueDate, Long assigneeUserId, Long targetStatusId) {
}
