package issue.tracker.system.core.model;

/**
 * Role of a user inside one project. The same user may hold different roles
 * in different projects.
 */
public enum ProjectRole {

    /** Works with the issues they created themselves. */
    DEVELOPER,

    /** Same rights as {@link #DEVELOPER}. */
    QA,

    /** Manages members and lifecycle, works with any issue of the project. */
    PROJECT_LEAD
}
