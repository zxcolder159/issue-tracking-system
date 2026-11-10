package issue.tracker.system.core.exception;

/**
 * Thrown when a requested entity (project, lifecycle, status, transition,
 * issue, membership) does not exist or has been soft-deleted.
 */
public class NotFoundException extends DomainException {

    /**
     * Creates the exception.
     *
     * @param message human-readable explanation shown to the user
     */
    public NotFoundException(String message) {
        super(message);
    }
}
