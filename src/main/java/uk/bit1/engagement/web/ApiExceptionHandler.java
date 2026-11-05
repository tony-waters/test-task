package uk.bit1.engagement.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Renders API errors as RFC 9457 ProblemDetail. Validation failures carry an {@code errors} array
 * of {@code {parameter, message}}. Applies to REST controllers only; pages handle their own.
 */
@RestControllerAdvice(annotations = RestController.class)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidParametersException.class)
  ProblemDetail invalidParameters(InvalidParametersException e) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "One or more request parameters are invalid.");
    problem.setTitle("Invalid request parameters");
    problem.setProperty("errors", e.getErrors());
    return problem;
  }
}
