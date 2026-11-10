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
 * Зарегистрированный аккаунт. Глобальные права задаёт {@link #systemRole}; права
 * внутри проекта определяются {@link Membership memberships} пользователя.
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

    /** Отображаемое имя, которое видят другие пользователи. */
    @Column(nullable = false)
    private String name;

    /** Логин, уникальный в масштабе системы. */
    @Column(nullable = false, unique = true)
    private String email;

    /** Хеш пароля, но не сам пароль в открытом виде. */
    @Column(nullable = false)
    private String password;

    /** Роль в масштабе системы; Admin действует как Project Lead в каждом проекте. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SystemRole systemRole;
}
