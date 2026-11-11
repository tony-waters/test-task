package uk.bit1.engagement.application.common;

/** One invalid request parameter, as reported in a ProblemDetail's {@code errors} array. */
public record ParameterError(String parameter, String message) {}
