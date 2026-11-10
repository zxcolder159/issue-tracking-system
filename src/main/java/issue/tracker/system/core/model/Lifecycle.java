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
 * The workflow graph of a project: ordered statuses (board columns) plus the
 * transitions between them (allowed moves). Exactly one lifecycle per project;
 * the board is a rendering of this graph.
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

    /** Owning project; unique, so one project has exactly one lifecycle. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, unique = true)
    private Project project;

    /** Board columns in board order; soft-deleted statuses are filtered out. */
    @Builder.Default
    @OneToMany(mappedBy = "lifecycle")
    @OrderBy("order ASC, id ASC")
    @SQLRestriction("deleted_at IS NULL")
    private List<Status> statuses = new ArrayList<>();

    /** Edges of the workflow graph; deleted together with the statuses they touch. */
    @Builder.Default
    @OneToMany(mappedBy = "lifecycle")
    private List<Transition> transitions = new ArrayList<>();
}
