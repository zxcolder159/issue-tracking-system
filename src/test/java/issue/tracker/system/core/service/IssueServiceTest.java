package issue.tracker.system.core.service;

import issue.tracker.system.core.exception.ForbiddenOperationException;
import issue.tracker.system.core.exception.RuleViolationException;
import issue.tracker.system.core.model.Issue;
import issue.tracker.system.core.model.Lifecycle;
import issue.tracker.system.core.model.Membership;
import issue.tracker.system.core.model.Project;
import issue.tracker.system.core.model.ProjectRole;
import issue.tracker.system.core.model.Status;
import issue.tracker.system.core.model.SystemRole;
import issue.tracker.system.core.model.Transition;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.repository.IssueRepository;
import issue.tracker.system.core.repository.LifecycleRepository;
import issue.tracker.system.core.repository.MembershipRepository;
import issue.tracker.system.core.repository.StatusRepository;
import issue.tracker.system.core.repository.TransitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IssueService}: creation in the first status, the
 * permission matrix for edit and status changes, and trash visibility rules.
 */
@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;
    @Mock
    private LifecycleRepository lifecycleRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private TransitionRepository transitionRepository;
    @Mock
    private MembershipRepository membershipRepository;

    private IssueService issueService;

    @BeforeEach
    void setUp() {
        AccessService accessService = new AccessService(membershipRepository);
        issueService = new IssueService(
                issueRepository, lifecycleRepository, statusRepository, transitionRepository,
                membershipRepository, accessService);
    }

    private final User developer = User.builder().id(100L).name("Dev").email("d@i.ly").password("x")
            .systemRole(SystemRole.USER).build();
    private final User stranger = User.builder().id(777L).name("Stranger").email("s@i.ly").password("x")
            .systemRole(SystemRole.USER).build();
    private final Project project = Project.builder().id(1L).name("Project").build();
    private final Membership devMembership = Membership.builder().id(200L).user(developer).project(project)
            .role(ProjectRole.DEVELOPER).build();
    private final Membership leadMembership = Membership.builder().id(201L).user(developer).project(project)
            .role(ProjectRole.PROJECT_LEAD).build();
    private final Lifecycle lifecycle = Lifecycle.builder().id(2L).project(project).build();
    private final Status todo = Status.builder().id(11L).name("To Do").order(0).lifecycle(lifecycle).build();
    private final Status done = Status.builder().id(12L).name("Done").order(1).lifecycle(lifecycle).build();

    private Issue issue(Membership author, Status status) {
        return Issue.builder().id(300L).title("  Fix login  ").description("d").type("BUG").priority("HIGH")
                .startDate(Instant.parse("2026-01-10T00:00:00Z"))
                .dueDate(Instant.parse("2026-01-20T00:00:00Z"))
                .author(author).assignee(null).project(project).status(status).build();
    }

    @Test
    void create_putsIssueInFirstStatusWithAuthorMembership() {
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(2L))
                .thenReturn(List.of(todo, done));
        when(issueRepository.save(any(Issue.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Issue created = issueService.create(developer, new IssueService.CreateCommand(
                1L, "  Fix login  ", "d", "BUG", "HIGH",
                Instant.parse("2026-01-10T00:00:00Z"), Instant.parse("2026-01-20T00:00:00Z"), null));

        assertThat(created.getStatus()).isEqualTo(todo);
        assertThat(created.getAuthor()).isEqualTo(devMembership);
        assertThat(created.getTitle()).isEqualTo("Fix login");
    }

    @Test
    void create_explainsWhyAdminWithoutMembershipCannotAuthorIssue() {
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(101L, 1L))
                .thenReturn(Optional.empty());
        User admin = User.builder().id(101L).name("Admin").email("a@i.ly").password("x")
                .systemRole(SystemRole.ADMIN).build();

        assertThatThrownBy(() -> issueService.create(admin, new IssueService.CreateCommand(
                1L, "Fix", null, "BUG", "HIGH",
                Instant.parse("2026-01-10T00:00:00Z"), Instant.parse("2026-01-20T00:00:00Z"), null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("администратор");
    }

    @Test
    void create_rejectsDueDateBeforeStartDate() {
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));

        assertThatThrownBy(() -> issueService.create(developer, new IssueService.CreateCommand(
                1L, "Fix", null, "BUG", "HIGH",
                Instant.parse("2026-01-20T00:00:00Z"), Instant.parse("2026-01-10T00:00:00Z"), null)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("окончания");
    }

    @Test
    void create_rejectsAssigneeOutsideProject() {
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(777L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueService.create(developer, new IssueService.CreateCommand(
                1L, "Fix", null, "BUG", "HIGH",
                Instant.parse("2026-01-10T00:00:00Z"), Instant.parse("2026-01-20T00:00:00Z"), 777L)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("assignee");
    }

    @Test
    void changeStatus_usesReverseEdgeAndChecksRole() {
        Issue own = issue(devMembership, todo);
        when(issueRepository.findByIdAndDeletedAtNull(300L)).thenReturn(Optional.of(own));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));
        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(done));
        // only the reverse edge Done -> To Do exists, which still allows the move
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 11L, 12L)).thenReturn(List.of());
        Transition edge = Transition.builder().lifecycle(lifecycle).from(done).to(todo)
                .allowRoles(Set.of(ProjectRole.DEVELOPER)).build();
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 12L, 11L)).thenReturn(List.of(edge));

        issueService.changeStatus(developer, 300L, 12L);

        assertThat(own.getStatus()).isEqualTo(done);
    }

    @Test
    void changeStatus_rejectsMoveWithoutEdge() {
        Issue own = issue(devMembership, todo);
        when(issueRepository.findByIdAndDeletedAtNull(300L)).thenReturn(Optional.of(own));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));
        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(done));
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 11L, 12L)).thenReturn(List.of());
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 12L, 11L)).thenReturn(List.of());

        assertThatThrownBy(() -> issueService.changeStatus(developer, 300L, 12L))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("не разрешён");
    }

    @Test
    void changeStatus_rejectsRoleMissingInAllowRoles() {
        Membership qaAuthor = Membership.builder().id(202L).user(developer).project(project)
                .role(ProjectRole.QA).build();
        Issue own = issue(qaAuthor, todo);
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(qaAuthor));
        when(issueRepository.findByIdAndDeletedAtNull(300L)).thenReturn(Optional.of(own));
        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(done));
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 11L, 12L))
                .thenReturn(List.of(Transition.builder().lifecycle(lifecycle).from(todo).to(done)
                        .allowRoles(Set.of(ProjectRole.PROJECT_LEAD)).build()));

        assertThatThrownBy(() -> issueService.changeStatus(developer, 300L, 12L))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("QA");
    }

    @Test
    void edit_developerCannotEditForeignIssue() {
        Membership foreignAuthor = Membership.builder().id(203L).user(stranger).project(project)
                .role(ProjectRole.DEVELOPER).build();
        Issue foreign = issue(foreignAuthor, todo);
        when(issueRepository.findByIdAndDeletedAtNull(300L)).thenReturn(Optional.of(foreign));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));

        assertThatThrownBy(() -> issueService.edit(developer, 300L, new IssueService.EditCommand(
                "New title", null, null, null, null, null, null, null)))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("только созданные ими");
    }

    @Test
    void edit_projectLeadEditsAnyIssueAndMovesItAlongTransition() {
        Membership foreignAuthor = Membership.builder().id(203L).user(stranger).project(project)
                .role(ProjectRole.DEVELOPER).build();
        Issue foreign = issue(foreignAuthor, todo);
        when(issueRepository.findByIdAndDeletedAtNull(300L)).thenReturn(Optional.of(foreign));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(leadMembership));
        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(done));
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 11L, 12L))
                .thenReturn(List.of(Transition.builder().lifecycle(lifecycle).from(todo).to(done)
                        .allowRoles(Set.of(ProjectRole.PROJECT_LEAD)).build()));

        Issue edited = issueService.edit(developer, 300L, new IssueService.EditCommand(
                "New title", null, null, null, null, null, null, 12L));

        assertThat(edited.getTitle()).isEqualTo("New title");
        assertThat(edited.getStatus()).isEqualTo(done);
    }

    @Test
    void trash_showsDeveloperOnlyOwnIssues() {
        Membership foreignAuthor = Membership.builder().id(203L).user(stranger).project(project)
                .role(ProjectRole.DEVELOPER).build();
        Issue own = issue(devMembership, todo);
        Issue foreign = issue(foreignAuthor, todo);
        own.setDeletedAt(Instant.now());
        foreign.setDeletedAt(Instant.now());
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));
        when(issueRepository.findByProjectIdAndDeletedAtNotNull(1L)).thenReturn(List.of(own, foreign));

        List<Issue> visible = issueService.trash(developer, 1L);

        assertThat(visible).containsExactly(own);
    }

    @Test
    void moveToTrash_softDeletesIssue() {
        Issue own = issue(devMembership, todo);
        when(issueRepository.findByIdAndDeletedAtNull(300L)).thenReturn(Optional.of(own));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));

        issueService.moveToTrash(developer, 300L);

        assertThat(own.getDeletedAt()).isNotNull();
    }

    @Test
    void restore_requiresTargetStatusWhenOriginalWasDeleted() {
        Issue own = issue(devMembership, todo);
        own.setDeletedAt(Instant.now());
        todo.setDeletedAt(Instant.now());
        when(issueRepository.findById(300L)).thenReturn(Optional.of(own));
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));

        assertThatThrownBy(() -> issueService.restore(developer, 300L, null))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("выберите статус");

        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(done));
        issueService.restore(developer, 300L, 12L);

        assertThat(own.getDeletedAt()).isNull();
        assertThat(own.getStatus()).isEqualTo(done);
    }

    @Test
    void restore_rejectsIssueOutsideTrash() {
        Issue own = issue(devMembership, todo);
        when(issueRepository.findById(300L)).thenReturn(Optional.of(own));
        lenient().when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(devMembership));

        assertThatThrownBy(() -> issueService.restore(developer, 300L, null))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("корзине");
    }
}
