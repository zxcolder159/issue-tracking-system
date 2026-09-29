package issue.tracker.system.core.service;

import issue.tracker.system.api.dto.ProjectDto;
import issue.tracker.system.core.exception.ResourceNotFoundException;
import issue.tracker.system.core.model.Project;
import issue.tracker.system.core.model.ProjectMember;
import issue.tracker.system.core.model.ProjectRole;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.ProjectMemberRepository;
import issue.tracker.system.core.repository.ProjectRepository;
import issue.tracker.system.core.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Testcontainers
@Transactional
class ProjectServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Autowired
    private UserRepository userRepository;

    private User admin;
    private User developer;

    @BeforeEach
    void setUp() {
        admin = userRepository.save(User.builder()
                .username("admin").email("admin@example.com").password("password").build());
        developer = userRepository.save(User.builder()
                .username("developer").email("developer@example.com").password("password").build());
    }

    @Test
    void createProjectMakesCreatorAdminMember() {
        ProjectDto created = projectService.createProject(new ProjectDto(null, "Tracker", "description"), admin.getId());

        assertNotNull(created.id());
        ProjectMember membership = projectMemberRepository
                .findByUserIdAndProjectId(admin.getId(), created.id())
                .orElseThrow();
        assertEquals(ProjectRole.ADMIN, membership.getProjectRole());
    }

    @Test
    void createProjectWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> projectService.createProject(new ProjectDto(null, null, "d"), admin.getId()));
    }

    @Test
    void createProjectWithBlankNameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> projectService.createProject(new ProjectDto(null, "   ", "d"), admin.getId()));
    }

    @Test
    void createProjectForUnknownUserThrowsAndSavesNothing() {
        long projectsBefore = projectRepository.count();

        assertThrows(ResourceNotFoundException.class,
                () -> projectService.createProject(new ProjectDto(null, "Ghost", null), -1L));

        assertEquals(projectsBefore, projectRepository.count());
    }

    @Test
    void updateProjectChangesOnlyNonNullFields() {
        Long projectId = createProjectByAdmin();

        projectService.updateProject(projectId, admin.getId(), new ProjectDto(null, null, "new description"));

        Project project = projectRepository.findById(projectId).orElseThrow();
        assertEquals("Old name", project.getName());
        assertEquals("new description", project.getDescription());
    }

    @Test
    void updateProjectIgnoresIdFromDto() {
        Long projectId = createProjectByAdmin();

        projectService.updateProject(projectId, admin.getId(), new ProjectDto(projectId + 1000, "Renamed", null));

        Project project = projectRepository.findById(projectId).orElseThrow();
        assertEquals("Renamed", project.getName());
        assertEquals(projectId, project.getId());
    }

    @Test
    void updateProjectByNonAdminMemberThrows() {
        Long projectId = createProjectByAdmin();
        addMember(developer, projectId, ProjectRole.DEVELOPER);

        assertThrows(AccessDeniedException.class,
                () -> projectService.updateProject(projectId, developer.getId(), new ProjectDto(null, "Hacked", null)));
    }

    @Test
    void updateProjectByNonMemberThrows() {
        Long projectId = createProjectByAdmin();

        assertThrows(AccessDeniedException.class,
                () -> projectService.updateProject(projectId, developer.getId(), new ProjectDto(null, "Hacked", null)));
    }

    @Test
    void updateProjectWithBlankNameThrows() {
        Long projectId = createProjectByAdmin();

        assertThrows(IllegalArgumentException.class,
                () -> projectService.updateProject(projectId, admin.getId(), new ProjectDto(null, "  ", null)));
    }

    @Test
    void updateUnknownProjectThrows() {
        assertThrows(ResourceNotFoundException.class,
                () -> projectService.updateProject(-1L, admin.getId(), new ProjectDto(null, "New", null)));
    }

    private Long createProjectByAdmin() {
        return projectService.createProject(new ProjectDto(null, "Old name", "old description"), admin.getId()).id();
    }

    private void addMember(User user, Long projectId, ProjectRole role) {
        projectMemberRepository.save(ProjectMember.builder()
                .project(projectRepository.getReferenceById(projectId))
                .user(user)
                .projectRole(role)
                .build());
    }
}
