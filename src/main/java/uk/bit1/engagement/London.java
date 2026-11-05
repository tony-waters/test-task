package uk.bit1.engagement;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

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

  /** The London calendar date an instant falls on. */
  public static LocalDate dateOf(Instant instant) {
    return LocalDate.ofInstant(instant, ZONE);
  }

  /** The Monday that starts the ISO week holding {@code date}. */
  public static LocalDate weekStart(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }

  /** The Monday that starts the London ISO week an instant falls in. */
  public static LocalDate weekOf(Instant instant) {
    return weekStart(dateOf(instant));
  }
}
