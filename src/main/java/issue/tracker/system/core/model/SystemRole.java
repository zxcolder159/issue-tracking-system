package issue.tracker.system.core.model;

/** System-wide role of an account, independent of any project. */
public enum SystemRole {

    /** The single system administrator; holds Project Lead rights in every project. */
    ADMIN,

    /** A regular account; project rights come from memberships. */
    USER
}
