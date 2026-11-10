package issue.tracker.system.core.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

/**
 * A registered account. Global rights come from {@link #systemRole}; rights
 * inside a project come from the user's {@link Membership memberships}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_seq_gen")
    @SequenceGenerator(name = "user_seq_gen", sequenceName = "user_seq")
    private Long id;

    /** Display name shown to other users. */
    @Column(nullable = false)
    private String name;

    /** Login identifier, unique across the system. */
    @Column(nullable = false, unique = true)
    private String email;

    /** Password hash, never the plain password. */
    @Column(nullable = false)
    private String password;

    /** System-wide role; the admin acts as Project Lead in every project. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemRole systemRole;
}
