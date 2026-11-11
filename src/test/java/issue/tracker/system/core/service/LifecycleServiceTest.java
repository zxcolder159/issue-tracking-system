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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-тесты для {@link LifecycleService}: правило порядка на доске для новых
 * статусов, дубли рёбер переходов и удаление статуса с переносом issues.
 */
@ExtendWith(MockitoExtension.class)
class LifecycleServiceTest {

    @Mock
    private LifecycleRepository lifecycleRepository;
    @Mock
    private StatusRepository statusRepository;
    @Mock
    private TransitionRepository transitionRepository;
    @Mock
    private IssueRepository issueRepository;
    @Mock
    private MembershipRepository membershipRepository;

    private LifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        AccessService accessService = new AccessService(membershipRepository);
        lifecycleService = new LifecycleService(
                lifecycleRepository, statusRepository, transitionRepository, issueRepository, accessService);
    }

    private final Project project = Project.builder().id(1L).name("Project").build();
    private final Lifecycle lifecycle = Lifecycle.builder().id(2L).project(project).build();
    private final Status todo = Status.builder().id(11L).name("To Do").order(0).lifecycle(lifecycle).build();
    private final Status inProgress = Status.builder().id(12L).name("In Progress").order(1).lifecycle(lifecycle).build();

    private User userWithRole(ProjectRole role) {
        User user = User.builder().id(100L).name("User").email("u@i.ly").password("x")
                .systemRole(SystemRole.USER).build();
        Membership membership = Membership.builder().id(200L).user(user).project(project).role(role).build();
        when(membershipRepository.findByUserIdAndProjectIdAndDeletedAtNull(100L, 1L))
                .thenReturn(Optional.of(membership));
        return user;
    }

    @Test
    void createStatus_isAppendedAsLastColumnAndConnectedToGivenStatuses() {
        User lead = userWithRole(ProjectRole.PROJECT_LEAD);
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(2L))
                .thenReturn(List.of(todo, inProgress));
        when(statusRepository.save(any(Status.class))).thenAnswer(invocation -> {
            Status status = invocation.getArgument(0);
            Status saved = Status.builder().id(13L).name(status.getName()).order(status.getOrder())
                    .lifecycle(status.getLifecycle()).build();
            return saved;
        });
        when(statusRepository.findByIdAndDeletedAtNull(11L)).thenReturn(Optional.of(todo));
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 13L, 11L)).thenReturn(List.of());

        Status created = lifecycleService.createStatus(lead, 1L, " In Review ", Set.of(11L),
                Set.of(ProjectRole.DEVELOPER));

        assertThat(created.getOrder()).isEqualTo(2);
        assertThat(created.getName()).isEqualTo("In Review");
        ArgumentCaptor<Transition> transitionCaptor = ArgumentCaptor.forClass(Transition.class);
        verify(transitionRepository).save(transitionCaptor.capture());
        Transition transition = transitionCaptor.getValue();
        assertThat(transition.getFrom().getId()).isEqualTo(13L);
        assertThat(transition.getTo().getId()).isEqualTo(11L);
        assertThat(transition.getAllowRoles()).containsExactly(ProjectRole.DEVELOPER);
    }

    @Test
    void createStatus_isRejectedForDeveloper() {
        User developer = userWithRole(ProjectRole.DEVELOPER);

        assertThatThrownBy(() -> lifecycleService.createStatus(developer, 1L, "New", null, null))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("Project Lead");
        verify(statusRepository, never()).save(any());
    }

    @Test
    void addTransition_rejectsDuplicateEdgeInEitherDirection() {
        User lead = userWithRole(ProjectRole.PROJECT_LEAD);
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByIdAndDeletedAtNull(11L)).thenReturn(Optional.of(todo));
        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(inProgress));
        // edge 12 -> 11 already exists, so adding 11 -> 12 is a duplicate
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 11L, 12L)).thenReturn(List.of());
        when(transitionRepository.findByLifecycleIdAndFromIdAndToId(2L, 12L, 11L))
                .thenReturn(List.of(Transition.builder().lifecycle(lifecycle).from(inProgress).to(todo).build()));

        assertThatThrownBy(() -> lifecycleService.addTransition(lead, 1L, 11L, 12L,
                Set.of(ProjectRole.DEVELOPER)))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("уже существует");
    }

    @Test
    void addTransition_requiresAtLeastOneRole() {
        User lead = userWithRole(ProjectRole.PROJECT_LEAD);

        assertThatThrownBy(() -> lifecycleService.addTransition(lead, 1L, 11L, 12L, Set.of()))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("роль");
    }

    @Test
    void deleteStatus_isRejectedForLastRemainingStatus() {
        User lead = userWithRole(ProjectRole.PROJECT_LEAD);
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByIdAndDeletedAtNull(11L)).thenReturn(Optional.of(todo));
        when(statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(2L)).thenReturn(List.of(todo));

        assertThatThrownBy(() -> lifecycleService.deleteStatus(lead, 1L, 11L, null))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("единственный");
    }

    @Test
    void deleteStatus_requiresTargetWhenIssuesExist() {
        User lead = userWithRole(ProjectRole.PROJECT_LEAD);
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByIdAndDeletedAtNull(11L)).thenReturn(Optional.of(todo));
        when(statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(2L))
                .thenReturn(List.of(todo, inProgress));
        when(issueRepository.findByStatusIdAndDeletedAtNull(11L)).thenReturn(List.of(Issue.builder().build()));

        assertThatThrownBy(() -> lifecycleService.deleteStatus(lead, 1L, 11L, null))
                .isInstanceOf(RuleViolationException.class)
                .hasMessageContaining("выберите статус для переноса");
    }

    @Test
    void deleteStatus_movesIssuesRemovesTouchingTransitionsAndSoftDeletes() {
        User lead = userWithRole(ProjectRole.PROJECT_LEAD);
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByIdAndDeletedAtNull(11L)).thenReturn(Optional.of(todo));
        when(statusRepository.findByIdAndDeletedAtNull(12L)).thenReturn(Optional.of(inProgress));
        when(statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(2L))
                .thenReturn(List.of(todo, inProgress));
        Issue issue = Issue.builder().id(300L).title("Bug").type("BUG").priority("HIGH")
                .startDate(Instant.EPOCH).dueDate(Instant.EPOCH).status(todo).project(project).build();
        when(issueRepository.findByStatusIdAndDeletedAtNull(11L)).thenReturn(List.of(issue));

        lifecycleService.deleteStatus(lead, 1L, 11L, 12L);

        assertThat(issue.getStatus()).isEqualTo(inProgress);
        verify(transitionRepository).deleteByFromId(11L);
        verify(transitionRepository).deleteByToId(11L);
        assertThat(todo.getDeletedAt()).isNotNull();
        assertThat(inProgress.getDeletedAt()).isNull();
    }

    @Test
    void deleteStatus_isAllowedForAdminWithoutMembership() {
        User admin = User.builder().id(101L).name("Admin").email("a@i.ly").password("x")
                .systemRole(SystemRole.ADMIN).build();
        when(lifecycleRepository.findByProjectId(1L)).thenReturn(Optional.of(lifecycle));
        when(statusRepository.findByIdAndDeletedAtNull(11L)).thenReturn(Optional.of(todo));
        when(statusRepository.findByLifecycleIdAndDeletedAtNullOrderByOrderAscIdAsc(2L))
                .thenReturn(List.of(todo, inProgress));
        when(issueRepository.findByStatusIdAndDeletedAtNull(11L)).thenReturn(List.of());

        lifecycleService.deleteStatus(admin, 1L, 11L, 12L);

        assertThat(todo.getDeletedAt()).isNotNull();
        verify(issueRepository, never()).findByIdAndDeletedAtNull(any());
    }
}
