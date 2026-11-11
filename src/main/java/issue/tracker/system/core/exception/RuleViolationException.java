package issue.tracker.system.core.exception;

/**
 * Выбрасывается, когда операция нарушает доменное правило: дублирующее ребро
 * перехода, срок завершения раньше даты начала, удаление последнего статуса
 * lifecycle, assignee вне проекта и подобные нарушения.
 */
public class RuleViolationException extends DomainException {

    /**
     * Создаёт исключение.
     *
     * @param message человекочитаемое объяснение для пользователя
     */
    public RuleViolationException(String message) {
        super(message);
    }
}
