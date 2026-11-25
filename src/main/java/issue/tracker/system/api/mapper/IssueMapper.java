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

/** MapStruct-маппер между HTTP-контрактом карточки задачи и типами {@link IssueService}. */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface IssueMapper {

    /**
     * Добавляет id проекта из URL к полям новой задачи.
     *
     * @param projectId id проекта из пути запроса
     * @param request тело запроса на создание задачи
     * @return команда создания в указанном проекте
     */
    IssueService.CreateCommand toCreateCommand(Long projectId, CreateIssueRequest request);

    /**
     * Преобразует тело запроса в команду частичного обновления, сохраняя значения {@code null}.
     *
     * @param request тело запроса на редактирование задачи
     * @return команда редактирования
     */
    IssueService.EditCommand toEditCommand(EditIssueRequest request);

    /**
     * Преобразует задачу и связанные сущности в поля карточки.
     *
     * @param issue задача, возвращённая сервисом
     * @return карточка задачи с вложенными DTO
     */
    IssueDto toIssueDto(Issue issue);

    /**
     * Преобразует участие в проекте для карточки задачи или списка исполнителей.
     *
     * @param membership участие пользователя в проекте
     * @return участие с ролью, данными пользователя и id проекта
     */
    @Mapping(target = "projectId", source = "project.id")
    MembershipDto toMembershipDto(Membership membership);

    /**
     * Преобразует список вариантов исполнителя.
     *
     * @param memberships активные участники проекта
     * @return DTO участников в порядке исходного списка
     */
    List<MembershipDto> toMembershipDtos(List<Membership> memberships);

    /**
     * Преобразует сведения о проекте задачи.
     *
     * @param project проект задачи
     * @return id, название и описание проекта
     */
    ProjectDto toProjectDto(Project project);

    /**
     * Преобразует статус для карточки задачи или списка допустимых переходов.
     *
     * @param status статус жизненного цикла
     * @return статус с позицией колонки и id проекта
     */
    @Mapping(target = "projectId", source = "project.id")
    StatusDto toStatusDto(Status status);

    /**
     * Преобразует список целевых статусов.
     *
     * @param statuses статусы, доступные для перехода
     * @return DTO статусов в порядке исходного списка
     */
    List<StatusDto> toStatusDtos(List<Status> statuses);

    /**
     * Преобразует данные автора или исполнителя для отображения в карточке.
     *
     * @param user пользователь, связанный с участием в проекте
     * @return id и отображаемое имя пользователя
     */
    UserSummaryDto toUserSummaryDto(User user);
}
