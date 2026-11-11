package issue.tracker.system.core.exception;

/**
 * Выбрасывается, когда запрошенная сущность (проект, lifecycle, статус,
 * переход, issue, участие) не существует или удалена через soft delete.
 */
public class NotFoundException extends DomainException {

    /**
     * Создаёт исключение.
     *
     * @param message человекочитаемое объяснение для пользователя
     */
    public NotFoundException(String message) {
        super(message);
    }
}
