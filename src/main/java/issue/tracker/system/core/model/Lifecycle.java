package issue.tracker.system.core.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.util.ArrayList;
import java.util.List;

/**
 * Граф процесса проекта: упорядоченные статусы (колонки доски) и переходы
 * между ними (допустимые перемещения). У проекта ровно один lifecycle;
 * board — отображение этого графа.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "lifecycles")
public class Lifecycle {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "lifecycle_seq_gen")
    @SequenceGenerator(name = "lifecycle_seq_gen", sequenceName = "lifecycle_seq")
    private Long id;

    /** Проект-владелец; уникален, поэтому у проекта ровно один lifecycle. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    /** Колонки доски в порядке на доске; статусы, удалённые через soft delete, отфильтрованы. */
    @Builder.Default
    @OneToMany(mappedBy = "lifecycle")
    @OrderBy("order ASC, id ASC")
    private List<Status> statuses = new ArrayList<>();

    /** Рёбра графа процесса; удаляются вместе со статусами, которых касаются. */
    @Builder.Default
    @OneToMany(mappedBy = "lifecycle")
    private List<Transition> transitions = new ArrayList<>();
}
