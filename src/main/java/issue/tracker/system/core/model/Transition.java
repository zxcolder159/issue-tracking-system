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
 * Ребро графа процесса между двумя статусами. Семантически двунаправленное:
 * одно ребро разрешает перемещение issues между {@link #from} и {@link #to}
 * в обе стороны.
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

    /** Один конец ребра; направление сохранённой строки смысла не несёт. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_status_id", nullable = false)
    private Status from;

    /** Другой конец ребра; направление сохранённой строки смысла не несёт. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_status_id", nullable = false)
    private Status to;

    /** Роли в проекте, которым разрешено перемещать issues по ребру; пустое множество — никому. */
    @Builder.Default
    @ElementCollection
    @CollectionTable(name = "transition_roles", joinColumns = @JoinColumn(name = "transition_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<ProjectRole> allowRoles = new HashSet<>();
}
