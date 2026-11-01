package studio.forma.web;

import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import studio.forma.inquiry.InquiryConflictException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail validation(MethodArgumentNotValidException exception) {
    var problem = problem(HttpStatus.BAD_REQUEST, "Проверьте заполнение полей.");
    var fields = new LinkedHashMap<String, String>();
    exception
        .getBindingResult()
        .getFieldErrors()
        .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
    problem.setProperty("errors", fields);
    return problem;
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    ConstraintViolationException.class,
    IllegalArgumentException.class
  })
  ProblemDetail badRequest(Exception exception) {
    return problem(HttpStatus.BAD_REQUEST, "Некорректный формат запроса.");
  }

  @ExceptionHandler({InquiryConflictException.class, OptimisticLockingFailureException.class})
  ProblemDetail conflict(Exception exception) {
    return problem(
        HttpStatus.CONFLICT,
        "Данные изменились или переход статуса недоступен. Обновите страницу.");
  }

  @ExceptionHandler(ResponseStatusException.class)
  ProblemDetail status(ResponseStatusException exception) {
    return ProblemDetail.forStatusAndDetail(
        exception.getStatusCode(),
        exception.getReason() == null ? "Запрос не выполнен." : exception.getReason());
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail unexpected(Exception exception) {
    if (exception instanceof org.springframework.web.ErrorResponse error) return error.getBody();
    // Do not log messages, payloads or SQL parameters that may contain client data.
    log.error("Request failed: {}", exception.getClass().getSimpleName());
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось выполнить запрос. Попробуйте позже.");
  }

  private ProblemDetail problem(HttpStatus status, String detail) {
    return ProblemDetail.forStatusAndDetail(status, detail);
  }
}
