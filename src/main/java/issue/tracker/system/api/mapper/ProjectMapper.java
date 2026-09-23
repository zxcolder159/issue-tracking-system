package issue.tracker.system.api.mapper;

import issue.tracker.system.api.dto.ProjectDto;
import issue.tracker.system.core.model.Project;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProjectMapper {

    @Mapping(target = "id", ignore = true)
    Project toEntity(ProjectDto projectDto);

    ProjectDto toDto(Project project);

    @Mapping(target = "id", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateProjectFromDto(ProjectDto dto, @MappingTarget Project project);

}
