package issue.tracker.system.core.exception;

/** Base exception for domain rule violations with a human-readable message. */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
