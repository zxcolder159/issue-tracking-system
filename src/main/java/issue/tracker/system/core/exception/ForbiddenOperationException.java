package issue.tracker.system.core.exception;

/**
 * Выбрасывается, когда у действующего пользователя нет прав на операцию:
 * он не участник проекта либо его роль (Developer, QA) этого не позволяет.
 */
public class ForbiddenOperationException extends DomainException {

    /**
     * Создаёт исключение.
     *
     * @param message человекочитаемое объяснение для пользователя
     */
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
