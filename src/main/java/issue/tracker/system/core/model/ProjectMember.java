package issue.tracker.system.core.model;

import jakarta.persistence.*;
import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "project_members", uniqueConstraints =
        {@UniqueConstraint(columnNames = {"user_id", "project_id"})})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "project_member_seq_gen")
    @SequenceGenerator(name = "project_member_seq_gen",
            sequenceName = "project_member_seq",
            allocationSize = 50)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectRole projectRole;
}