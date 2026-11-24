package issue.tracker.system.api.dto;

import java.time.Instant;

public record CreateIssueRequest(String title, String description, String type, String priority,
                                 Instant startDate, Instant dueDate, Long assigneeUserId) {
}
