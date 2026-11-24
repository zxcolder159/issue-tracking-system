package issue.tracker.system.api.controller;

import issue.tracker.system.api.dto.CreateIssueRequest;
import issue.tracker.system.api.dto.EditIssueRequest;
import issue.tracker.system.api.dto.IssueDto;
import issue.tracker.system.api.dto.MembershipDto;
import issue.tracker.system.api.dto.StatusDto;
import issue.tracker.system.api.mapper.IssueMapper;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueMapper issueMapper;

    @GetMapping("/issues/{issueId}")
    public IssueDto openIssue(@AuthenticationPrincipal(expression = "actor") User actor, @PathVariable Long issueId) {
        return issueMapper.toIssueDto(issueService.getIssue(actor, issueId));
    }

    @PostMapping("/projects/{projectId}/issues")
    public ResponseEntity<IssueDto> createIssue(@AuthenticationPrincipal(expression = "actor") User actor,
                                                @PathVariable Long projectId,
                                                @RequestBody CreateIssueRequest request) {
        IssueDto created = issueMapper.toIssueDto(issueService.create(actor, issueMapper.toCreateCommand(projectId, request)));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/issues/{issueId}").buildAndExpand(created.id()).toUri()).body(created);
    }

    @PatchMapping("/issues/{issueId}")
    public IssueDto saveChanges(@AuthenticationPrincipal(expression = "actor") User actor,
                                @PathVariable Long issueId, @RequestBody EditIssueRequest request) {
        return issueMapper.toIssueDto(issueService.edit(actor, issueId, issueMapper.toEditCommand(request)));
    }

    @GetMapping("/projects/{projectId}/issues/assignees")
    public List<MembershipDto> showAssignees(@AuthenticationPrincipal(expression = "actor") User actor,
                                             @PathVariable Long projectId) {
        return issueMapper.toMembershipDtos(issueService.getAssignableMembers(actor, projectId));
    }

    @GetMapping("/issues/{issueId}/statuses")
    public List<StatusDto> showStatuses(@AuthenticationPrincipal(expression = "actor") User actor,
                                        @PathVariable Long issueId) {
        return issueMapper.toStatusDtos(issueService.getAllowedStatuses(actor, issueId));
    }

    @DeleteMapping("/issues/{issueId}")
    public IssueDto moveToTrash(@AuthenticationPrincipal(expression = "actor") User actor,
                                @PathVariable Long issueId) {
        return issueMapper.toIssueDto(issueService.moveToTrash(actor, issueId));
    }
}
