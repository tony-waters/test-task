package uk.bit1.engagement;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The time zone every display, date filter, weekly grouping and age calculation uses. Stored
 * instants are UTC (ADR 0001).
 */
public final class London {

  public static final ZoneId ZONE = ZoneId.of("Europe/London");

  private London() {}

  public static LocalDate today(Clock clock) {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** The first instant of the given London calendar date. */
  public static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZONE).toInstant();
  }
}
