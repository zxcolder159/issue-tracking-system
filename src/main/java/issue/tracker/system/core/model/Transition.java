package issue.tracker.system.core.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * An edge of the workflow graph between two statuses. Semantically
 * bidirectional: a single edge allows moving issues between {@link #from} and
 * {@link #to} in both directions.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "transitions")
public class Transition {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "transition_seq_gen")
    @SequenceGenerator(name = "transition_seq_gen", sequenceName = "transition_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lifecycle_id", nullable = false)
    private Lifecycle lifecycle;

    /** One end of the edge; direction of the stored row carries no meaning. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_status_id", nullable = false)
    private Status from;

    /** The other end of the edge; direction of the stored row carries no meaning. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_status_id", nullable = false)
    private Status to;

    /** Project roles allowed to move issues along the edge; empty set — nobody. */
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "transition_roles", joinColumns = @JoinColumn(name = "transition_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<ProjectRole> allowRoles = new HashSet<>();
}
