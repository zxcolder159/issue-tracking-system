package issue.tracker.system.core.service;

import issue.tracker.system.api.dto.ProjectDto;
import issue.tracker.system.api.mapper.ProjectMapper;
import issue.tracker.system.core.exception.ResourceNotFoundException;
import issue.tracker.system.core.model.Project;
import issue.tracker.system.core.model.ProjectMember;
import issue.tracker.system.core.model.ProjectRole;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.ProjectMemberRepository;
import issue.tracker.system.core.repository.ProjectRepository;
import issue.tracker.system.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private static final int NAME_MAX_LENGTH = 255;

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;

    @Transactional
    public ProjectDto createProject(ProjectDto projectDto, Long currentUserId) {
        if (projectDto.name() == null) {
            throw new IllegalArgumentException("Project name is required");
        }
        validateName(projectDto.name());

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + currentUserId));

        Project project = projectRepository.save(projectMapper.toEntity(projectDto));
        projectMemberRepository.save(ProjectMember.builder()
                .project(project)
                .user(user)
                .projectRole(ProjectRole.ADMIN)
                .build());
        return projectMapper.toDto(project);
    }

    @Transactional
    public void updateProject(Long projectId, Long currentUserId, ProjectDto projectDto) {
        if (projectDto.name() != null) {
            validateName(projectDto.name());
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));
        requireAdminRole(projectId, currentUserId);

        projectMapper.updateProjectFromDto(projectDto, project);
    }

    private void requireAdminRole(Long projectId, Long currentUserId) {
        ProjectMember membership = projectMemberRepository.findByUserIdAndProjectId(currentUserId, projectId)
                .orElseThrow(() -> new AccessDeniedException(
                        "User with id: " + currentUserId + " is not a member of project with id: " + projectId));
        if (membership.getProjectRole() != ProjectRole.ADMIN) {
            throw new AccessDeniedException(
                    "User with id: " + currentUserId + " has no permission to change project with id: " + projectId);
        }
    }

    private void validateName(String name) {
        if (name.isBlank() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Project name must not be blank and must not exceed " + NAME_MAX_LENGTH + " characters");
        }
    }
}
