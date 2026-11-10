package issue.tracker.system.core.exception;

/**
 * Базовое runtime-исключение для всех доменных ошибок issue tracker.
 *
 * <p>Каждый наследник содержит человекочитаемое сообщение: по концепции
 * проекта пользователю всегда объясняют причину отказа, тихих ошибок нет.
 */
public class DomainException extends RuntimeException {

    /**
     * Создаёт исключение.
     *
     * @param message человекочитаемое объяснение для пользователя
     */
    public DomainException(String message) {
        super(message);
    }
}
