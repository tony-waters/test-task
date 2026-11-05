package uk.bit1.engagement.patient;

import java.time.LocalDate;
import java.time.Period;

/** A Patient's current age in whole years. Unknown when the date of birth is unknown. */
public final class Age {

  private Age() {}

  /** Whole years from {@code dateOfBirth} to {@code today}, or null when the birth date is null. */
  public static Integer inYears(LocalDate dateOfBirth, LocalDate today) {
    if (dateOfBirth == null) {
      return null;
    }
    return Period.between(dateOfBirth, today).getYears();
  }
}
