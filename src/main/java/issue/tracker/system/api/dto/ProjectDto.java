package issue.tracker.system.api.dto;

/**
 * Сведения о проекте в карточке задачи.
 *
 * @param id id проекта
 * @param name название проекта
 * @param description описание проекта, может быть {@code null}
 */
public record ProjectDto(Long id, String name, String description) {
}
