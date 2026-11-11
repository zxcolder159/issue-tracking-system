package issue.tracker.system.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Проект — изолированное пространство со своими участниками, ролями и lifecycle.
 * Удаление — soft delete: участники теряют доступ сразу, данные остаются.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "project_seq_gen")
    @SequenceGenerator(name = "project_seq_gen", sequenceName = "project_seq")
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Проставляется автоматически при первой вставке и больше не меняется. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Признак soft delete; не null означает, что проект удалён. */
    private Instant deletedAt;
}
