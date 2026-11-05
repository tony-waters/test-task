package uk.bit1.engagement.web;

/** The requested resource does not exist. Rendered as a 404 ProblemDetail. */
public class NotFoundException extends RuntimeException {

  /** @param detail safe to show to the caller: never include identifiers or patient data */
  public NotFoundException(String detail) {
    super(detail);
  }
}
