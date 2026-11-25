package issue.tracker.system.api.dto;

import java.time.Instant;

/**
 * Тело запроса на создание задачи. Сервис определяет автора по действующему
 * участнику проекта и выбирает первый статус жизненного цикла.
 *
 * @param title обязательное название
 * @param description необязательное описание задачи
 * @param type обязательный тип задачи
 * @param priority обязательный приоритет задачи
 * @param startDate обязательная дата начала работ
 * @param dueDate обязательная дата окончания, равная дате начала или более поздняя
 * @param assigneeUserId id пользователя-исполнителя из активных участников проекта, если указан
 */
public record CreateIssueRequest(String title, String description, String type, String priority,
                                 Instant startDate, Instant dueDate, Long assigneeUserId) {
}
