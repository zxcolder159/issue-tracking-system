package issue.tracker.system.api.dto;

public record StatusDto(Long id, String name, Integer order, Long projectId) {
}
