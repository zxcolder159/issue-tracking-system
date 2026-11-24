package issue.tracker.system.api.dto;

import issue.tracker.system.core.model.ProjectRole;

public record MembershipDto(Long id, ProjectRole role, UserSummaryDto user, Long projectId) {
}
