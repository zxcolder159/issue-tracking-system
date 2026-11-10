package issue.tracker.system.core.exception;

/**
 * Base runtime exception for all domain-level failures of the issue tracker.
 *
 * <p>Every subclass carries a human-readable message: per the project vision
 * a rejection must always be explained to the user, there are no silent errors.
 */
public class DomainException extends RuntimeException {

    /**
     * Creates the exception.
     *
     * @param message human-readable explanation shown to the user
     */
    public DomainException(String message) {
        super(message);
    }
}
