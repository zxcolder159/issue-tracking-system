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
 * Рабочий элемент проекта. Автор и assignee — участия (пользователь в
 * проекте), поэтому сохраняют смысл даже после того, как assignee покидает
 * проект. Удаление — soft delete: issue попадает в корзину на 7 дней.
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

    /** Свободное описание с ограниченным Markdown (жирный, курсив, ссылки). */
    @Column(columnDefinition = "text")
    private String description;

    /** Значение типа (Bug, Task, Story...); словарь значений — вопрос конкретного заказчика. */
    @Column(nullable = false)
    private String type;

    /** Значение приоритета (Critical, High...); словарь значений — вопрос конкретного заказчика. */
    @Column(nullable = false)
    private String priority;

    /** Начало рабочего окна; {@link #dueDate} не может быть раньше него. */
    @Column(nullable = false)
    private Instant startDate;

    @Column(nullable = false)
    private Instant dueDate;

    /** Участие пользователя, создавшего issue; определяет права на редактирование для Developer и QA. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Membership author;

    /** Участие пользователя, на которого назначен issue; необязательное. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Membership assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /** Текущая колонка доски; меняется только по допустимым переходам. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "status_id", nullable = false)
    private Status status;

    /** Проставляется автоматически при первой вставке и больше не меняется. */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** Признак soft delete; не null означает, что issue находится в корзине. */
    private Instant deletedAt;
}
