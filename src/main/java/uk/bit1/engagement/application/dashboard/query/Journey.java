package uk.bit1.engagement.application.dashboard.query;

import java.time.Instant;

/** The three journey timestamps of one Patient: all the dashboard needs to know about them. */
public record Journey(Instant invitedAt, Instant registeredAt, Instant dischargedAt) {

  /** Discharged without ever registering. */
  public boolean skippedRegistration() {
    return dischargedAt != null && registeredAt == null;
  }
}
