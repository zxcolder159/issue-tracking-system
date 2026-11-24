package issue.tracker.system.api.dto;

import issue.tracker.system.core.model.ProjectRole;

/**
 * Участие пользователя в проекте в карточке задачи и в списке исполнителей.
 *
 * @param id id участия
 * @param role роль пользователя в проекте
 * @param user id и отображаемое имя пользователя
 * @param projectId id проекта, в котором действует роль
 */
public record MembershipDto(Long id, ProjectRole role, UserSummaryDto user, Long projectId) {
}
