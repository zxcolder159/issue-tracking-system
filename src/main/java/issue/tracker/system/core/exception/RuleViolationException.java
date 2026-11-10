package issue.tracker.system.core.exception;

/**
 * Thrown when an operation breaks a domain rule: a duplicate transition edge,
 * a due date before the start date, deletion of the last lifecycle status,
 * an assignee outside the project and similar violations.
 */
public class RuleViolationException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param message human-readable explanation shown to the user
     */
    public RuleViolationException(String message) {
        super(message);
    }
}
