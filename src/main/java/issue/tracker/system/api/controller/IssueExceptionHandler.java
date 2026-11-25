package issue.tracker.system.api.controller;

import issue.tracker.system.core.exception.ForbiddenOperationException;
import issue.tracker.system.core.exception.NotFoundException;
import issue.tracker.system.core.exception.RuleViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Преобразует доменные исключения {@link IssueController} в HTTP-ответы Problem Details. */
@RestControllerAdvice(assignableTypes = IssueController.class)
public class IssueExceptionHandler {

    /**
     * Обрабатывает запрос к отсутствующей или удалённой сущности.
     *
     * @param exception доменная ошибка с объяснением
     * @return {@code 404 Not Found} с сообщением сервиса в поле {@code detail}
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail notFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * Обрабатывает отказ в доступе к проекту или операции с задачей.
     *
     * @param exception доменная ошибка с причиной отказа
     * @return {@code 403 Forbidden} с сообщением сервиса в поле {@code detail}
     */
    @ExceptionHandler(ForbiddenOperationException.class)
    public ProblemDetail forbidden(ForbiddenOperationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
    }

    /**
     * Обрабатывает нарушение правил задачи, например недопустимые сроки или смену статуса.
     *
     * @param exception доменная ошибка с описанием нарушенного правила
     * @return {@code 422 Unprocessable Content} с сообщением сервиса в поле {@code detail}
     */
    @ExceptionHandler(RuleViolationException.class)
    public ProblemDetail ruleViolation(RuleViolationException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, exception.getMessage());
    }
}
