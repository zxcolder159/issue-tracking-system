package issue.tracker.system.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A work item of a project. Author and assignee are memberships (a user in a
 * project), so they keep their meaning even after the assignee leaves the
 * project. Deletion is a soft delete: the issue goes to the trash for 7 days.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "issue_seq_gen")
    @SequenceGenerator(name = "issue_seq_gen", sequenceName = "issue_seq")
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Free-form description with limited Markdown (bold, italic, links). */
    @Column(columnDefinition = "text")
    private String description;

    /** Type value (Bug, Task, Story...); the vocabulary is a per-customer question. */
    @Column(nullable = false)
    private String type;

    /** Priority value (Critical, High...); the vocabulary is a per-customer question. */
    @Column(nullable = false)
    private String priority;

    /** Start of the work window; {@link #dueDate} must not precede it. */
    @Column(nullable = false)
    private Instant startDate;

    @Column(nullable = false)
    private Instant dueDate;

    /** Membership of the user who created the issue; defines edit rights for Developer and QA. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Membership author;

    /** Membership of the user the issue is assigned to; optional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Membership assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Current column of the board; changes only along allowed transitions. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

    /** Set automatically on the first insert, never changes afterwards. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Soft delete marker; non-null means the issue is in the trash. */
    private Instant deletedAt;
}
