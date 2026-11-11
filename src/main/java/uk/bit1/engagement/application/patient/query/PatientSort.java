package uk.bit1.engagement.application.patient.query;

import java.util.Arrays;
import java.util.Optional;

/**
 * The columns the patient list can be sorted by, with the request value and the JPQL expression
 * for each. Unknown values always sort last, whatever the direction.
 */
public enum PatientSort {
  FAMILY_NAME("familyName", "lower(p.familyName)", false),
  GIVEN_NAME("givenName", "lower(p.givenName)", false),
  INVITED_AT("invitedAt", "p.invitedAt", false),
  STATUS(
      "status",
      "case p.status"
          + " when uk.bit1.engagement.domain.patient.Status.INVITED then 0"
          + " when uk.bit1.engagement.domain.patient.Status.REGISTERED then 1"
          + " else 2 end",
      false),
  /** Age ascending means youngest first, so the date of birth sorts the other way. */
  AGE("age", "p.dateOfBirth", true),
  LAST_ACTION_AT("lastActionAt", "lastActionAt", false);

  private final String param;
  private final String expression;
  private final boolean inverted;

  PatientSort(String param, String expression, boolean inverted) {
    this.param = param;
    this.expression = expression;
    this.inverted = inverted;
  }

  public String param() {
    return param;
  }

  /** The JPQL {@code order by} item for this column in the given direction. */
  public String orderBy(SortDirection direction) {
    SortDirection effective = inverted ? direction.reversed() : direction;
    return expression + " " + effective.name().toLowerCase() + " nulls last";
  }

  public static Optional<PatientSort> fromParam(String value) {
    return Arrays.stream(values()).filter(s -> s.param.equals(value)).findFirst();
  }

  public static String allowedValues() {
    return String.join(", ", Arrays.stream(values()).map(PatientSort::param).toList());
  }
}
