package issue.tracker.system.api.dto;

import java.time.Instant;

/**
 * Карточка задачи, которую API возвращает при просмотре и после изменений.
 *
 * @param id id задачи
 * @param title название задачи
 * @param description описание задачи, может быть {@code null}
 * @param type тип задачи
 * @param priority приоритет задачи
 * @param startDate дата начала работ
 * @param dueDate дата окончания работ
 * @param author участие автора в проекте
 * @param assignee участие исполнителя в проекте или {@code null} для задачи без исполнителя
 * @param project проект, которому принадлежит задача
 * @param status текущий статус задачи
 * @param createdAt время создания задачи
 * @param deletedAt время перемещения в корзину или {@code null} для активной задачи
 */
public record IssueDto(Long id, String title, String description, String type, String priority,
                       Instant startDate, Instant dueDate, MembershipDto author, MembershipDto assignee,
                       ProjectDto project, StatusDto status, Instant createdAt, Instant deletedAt) {
}
