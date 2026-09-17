package issue.tracker.system.core.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "issue_statuses")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IssueStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "issue_status_seq_gen")
    @SequenceGenerator(name = "issue_status_seq_gen",
            sequenceName = "issue_status_seq",
            allocationSize = 50)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer orderIndex;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
