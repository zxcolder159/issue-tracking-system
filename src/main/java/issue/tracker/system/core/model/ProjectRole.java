package issue.tracker.system.core.model;

/**
 * Роль пользователя внутри одного проекта. Один пользователь может иметь
 * разные роли в разных проектах.
 */
public enum ProjectRole {

    /** Работает с issues, которые создал сам. */
    DEVELOPER,

    /** Те же права, что у {@link #DEVELOPER}. */
    QA,

    /** Управляет участниками и lifecycle, работает с любым issue проекта. */
    PROJECT_LEAD
}
