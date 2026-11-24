package issue.tracker.system.api.dto;

import java.time.Instant;

public record IssueDto(Long id, String title, String description, String type, String priority,
                       Instant startDate, Instant dueDate, MembershipDto author, MembershipDto assignee,
                       ProjectDto project, StatusDto status, Instant createdAt, Instant deletedAt) {
}
