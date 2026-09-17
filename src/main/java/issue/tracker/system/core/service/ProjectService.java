package issue.tracker.system.core.service;

import issue.tracker.system.api.dto.ProjectDto;
import issue.tracker.system.api.mapper.ProjectMapper;
import issue.tracker.system.core.exception.ResourceNotFound;
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

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    @Transactional
    public ProjectDto createProject(String name, String description, Long currentUserId) {
        Project project = Project.builder()
                .name(name)
                .description(description)
                .build();
        projectRepository.save(project);

        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFound("User not found with id: " + currentUserId));

        ProjectMember projectMember = ProjectMember.builder()
                .project(project)
                .user(user)
                .projectRole(ProjectRole.ADMIN)
                .build();
        projectMemberRepository.save(projectMember);
        return projectMapper.toDto(project);

    }

    @Transactional
    public void changeProject(Long projectId, Long currentUserId, ProjectDto projectDto) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFound("Project not found with id: " + projectId));
        ProjectMember projectMember = projectMemberRepository.findByUserIdAndProjectId(currentUserId, projectId)
                .orElseThrow(() -> new ResourceNotFound("User not found with id: " + currentUserId));
       if(projectMember.getProjectRole() != ProjectRole.ADMIN) {
           throw new AccessDeniedException("User with id: " + currentUserId + " does not have permission to change project with id: " + projectId);
       }
        projectMapper.updateProjectFromDto(projectDto, project);
        projectRepository.save(project);
    }

}
