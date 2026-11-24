package issue.tracker.system.api.mapper;

import issue.tracker.system.api.dto.CreateIssueRequest;
import issue.tracker.system.api.dto.EditIssueRequest;
import issue.tracker.system.api.dto.IssueDto;
import issue.tracker.system.api.dto.MembershipDto;
import issue.tracker.system.api.dto.ProjectDto;
import issue.tracker.system.api.dto.StatusDto;
import issue.tracker.system.api.dto.UserSummaryDto;
import issue.tracker.system.core.model.Issue;
import issue.tracker.system.core.model.Membership;
import issue.tracker.system.core.model.Project;
import issue.tracker.system.core.model.Status;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.service.IssueService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IssueMapper {

    IssueService.CreateCommand toCreateCommand(Long projectId, CreateIssueRequest request);

    IssueService.EditCommand toEditCommand(EditIssueRequest request);

    IssueDto toIssueDto(Issue issue);

    @Mapping(target = "projectId", source = "project.id")
    MembershipDto toMembershipDto(Membership membership);

    List<MembershipDto> toMembershipDtos(List<Membership> memberships);

    ProjectDto toProjectDto(Project project);

    @Mapping(target = "projectId", source = "project.id")
    StatusDto toStatusDto(Status status);

    List<StatusDto> toStatusDtos(List<Status> statuses);

    UserSummaryDto toUserSummaryDto(User user);
}
