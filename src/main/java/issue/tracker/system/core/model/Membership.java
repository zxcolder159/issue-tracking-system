package issue.tracker.system.core.model;

import jakarta.persistence.Column;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Membership of a user in a project with a per-project role. The same person
 * may hold different roles in different projects.
 *
 * <p>Removing a member is a soft delete: the user loses access immediately,
 * but the issues they created keep pointing at this membership. Because of the
 * unique constraint on (user, project) a removed member is added back by
 * reactivating the same row, not by inserting a new one.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "memberships", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "project_id"}))
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "membership_seq_gen")
    @SequenceGenerator(name = "membership_seq_gen", sequenceName = "membership_seq")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Rights of the user inside this particular project. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole role;

    /** Soft delete marker; non-null means the user was removed from the project. */
    private Instant deletedAt;
}
