package uk.bit1.engagement.core;

import java.time.Instant;

/**
 * A Patient's current stage: the latest of Invited, Registered and Discharged they have reached.
 *
 * <p>The database holds the same rule as a generated column on {@code patient.status}; the two
 * must agree.
 */
public enum Status {
  INVITED,
  REGISTERED,
  DISCHARGED;

  /**
   * Derives Status from the journey timestamps. A Patient discharged without registering (skipped
   * registration) is Discharged.
   */
  public static Status derive(Instant registeredAt, Instant dischargedAt) {
    if (dischargedAt != null) {
      return DISCHARGED;
    }
    if (registeredAt != null) {
      return REGISTERED;
    }
    return INVITED;
  }
}
