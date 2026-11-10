package issue.tracker.system.core.exception;

/**
 * Thrown when the acting user lacks the rights for the operation: they are
 * not a member of the project, or their role (Developer, QA) does not allow it.
 */
public class ForbiddenOperationException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param message human-readable explanation shown to the user
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
