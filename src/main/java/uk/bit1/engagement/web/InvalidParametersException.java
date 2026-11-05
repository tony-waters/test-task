package uk.bit1.engagement.web;

import java.util.List;

/** One or more request parameters are invalid. Rendered as a 400 ProblemDetail. */
public class InvalidParametersException extends RuntimeException {

  private final transient List<ParameterError> errors;

  public InvalidParametersException(List<ParameterError> errors) {
    super("Invalid request parameters: " + errors.size());
    this.errors = List.copyOf(errors);
  }

  public List<ParameterError> getErrors() {
    return errors;
  }
}
