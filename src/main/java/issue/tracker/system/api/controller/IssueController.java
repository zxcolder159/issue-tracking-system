package issue.tracker.system.api.controller;

import issue.tracker.system.api.dto.CreateIssueRequest;
import issue.tracker.system.api.dto.EditIssueRequest;
import issue.tracker.system.api.dto.IssueDto;
import issue.tracker.system.api.dto.MembershipDto;
import issue.tracker.system.api.dto.StatusDto;
import issue.tracker.system.api.dto.UserSummaryDto;
import issue.tracker.system.api.mapper.IssueMapper;
import issue.tracker.system.core.model.User;
import issue.tracker.system.core.service.IssueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
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

/**
 * REST API карточки задачи для сценариев UC-24, UC-26, UC-27 и UC-30.
 * Spring Security передаёт контроллеру действующего пользователя, а {@link IssueService}
 * проверяет права на операции и применяет правила проекта. Доменные ошибки преобразует
 * {@link IssueExceptionHandler}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class IssueController {

    private final IssueService issueService;
    private final IssueMapper issueMapper;

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @GetMapping("/projects/{projectId}/issues")
    public List<IssueDto> listIssues(@AuthenticationPrincipal(expression = "actor") User actor,
                                     @PathVariable Long projectId) {
        return issueService.findIssues(actor, projectId, null).stream().map(issueMapper::toIssueDto).toList();
    }

    @GetMapping("/projects/{projectId}/statuses")
    public List<StatusDto> projectStatuses(@AuthenticationPrincipal(expression = "actor") User actor,
                                          @PathVariable Long projectId) {
        return issueMapper.toStatusDtos(issueService.getProjectStatuses(actor, projectId));
    }

    /**
     * Открывает карточку активной задачи. Возвращает {@code 403}, если пользователю
     * недоступен проект, и {@code 404}, если задача отсутствует или лежит в корзине.
     *
     * @param actor действующий пользователь
     * @param issueId id задачи
     * @return поля карточки, включая текущий статус, автора и исполнителя
     */
    @GetMapping("/issues/{issueId}")
    public IssueDto openIssue(@AuthenticationPrincipal(expression = "actor") User actor, @PathVariable Long issueId) {
        return issueMapper.toIssueDto(issueService.getIssue(actor, issueId));
    }

    /**
     * Создаёт задачу в первом статусе жизненного цикла. Автором становится
     * действующий участник проекта.
     *
     * @param actor действующий пользователь, состоящий в проекте
     * @param projectId id проекта
     * @param request поля новой задачи
     * @return ответ {@code 201 Created} с карточкой задачи и её URI в заголовке {@code Location}
     */
    @PostMapping("/projects/{projectId}/issues")
    public ResponseEntity<IssueDto> createIssue(@AuthenticationPrincipal(expression = "actor") User actor,
                                                @PathVariable Long projectId,
                                                @RequestBody CreateIssueRequest request) {
        IssueDto created = issueMapper.toIssueDto(issueService.create(actor, issueMapper.toCreateCommand(projectId, request)));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/issues/{issueId}").buildAndExpand(created.id()).toUri()).body(created);
    }

    /**
     * Сохраняет частичное обновление задачи. Сервис проверяет сроки, участие
     * исполнителя в проекте и разрешение на переход в указанный статус.
     *
     * @param actor действующий пользователь с правом редактирования задачи
     * @param issueId id задачи
     * @param request поля, которые нужно изменить
     * @return карточка после сохранения изменений
     */
    @PatchMapping("/issues/{issueId}")
    public IssueDto saveChanges(@AuthenticationPrincipal(expression = "actor") User actor,
                                @PathVariable Long issueId, @RequestBody EditIssueRequest request) {
        return issueMapper.toIssueDto(issueService.edit(actor, issueId, issueMapper.toEditCommand(request)));
    }

    /**
     * Возвращает варианты исполнителя для формы создания или редактирования задачи.
     * Для назначения клиент передаёт {@link UserSummaryDto#id() id пользователя}
     * из поля {@link MembershipDto#user() user}.
     *
     * @param actor действующий пользователь с доступом к проекту
     * @param projectId id проекта
     * @return активные участники проекта в порядке имени пользователя, затем id участия
     */
    @GetMapping("/projects/{projectId}/issues/assignees")
    public List<MembershipDto> showAssignees(@AuthenticationPrincipal(expression = "actor") User actor,
                                             @PathVariable Long projectId) {
        return issueMapper.toMembershipDtos(issueService.getAssignableMembers(actor, projectId));
    }

    /**
     * Возвращает статусы, доступные из текущего статуса задачи по разрешённым
     * для роли пользователя двунаправленным переходам.
     *
     * @param actor действующий пользователь с правом редактирования задачи
     * @param issueId id задачи
     * @return целевые статусы в порядке колонок доски, затем по id
     */
    @GetMapping("/issues/{issueId}/statuses")
    public List<StatusDto> showStatuses(@AuthenticationPrincipal(expression = "actor") User actor,
                                        @PathVariable Long issueId) {
        return issueMapper.toStatusDtos(issueService.getAllowedStatuses(actor, issueId));
    }

    /**
     * Переносит задачу в корзину, проставляя время удаления. После этого
     * карточка недоступна через {@link #openIssue(User, Long)}.
     *
     * @param actor действующий пользователь с правом редактирования задачи
     * @param issueId id задачи
     * @return карточка с заполненным полем {@code deletedAt}
     */
    @DeleteMapping("/issues/{issueId}")
    public IssueDto moveToTrash(@AuthenticationPrincipal(expression = "actor") User actor,
                                @PathVariable Long issueId) {
        return issueMapper.toIssueDto(issueService.moveToTrash(actor, issueId));
    }
}
