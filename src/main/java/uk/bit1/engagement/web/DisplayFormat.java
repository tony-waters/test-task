package uk.bit1.engagement.web;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;
import uk.bit1.engagement.domain.common.London;

/**
 * Formats values for pages, in London time and UK English whatever the server locale. Used from
 * templates as {@code @fmt}.
 */
@Component("fmt")
public class DisplayFormat {

  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK).withZone(London.ZONE);
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK).withZone(London.ZONE);
  private static final DateTimeFormatter SECONDS =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.UK).withZone(London.ZONE);
  private static final DateTimeFormatter CALENDAR_DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

  public String dateTime(Instant instant) {
    return instant == null ? "" : DATE_TIME.format(instant);
  }

  public String date(Instant instant) {
    return instant == null ? "" : DATE.format(instant);
  }

  /** To the second, for timelines, where several Actions can share a minute. */
  public String seconds(Instant instant) {
    return instant == null ? "" : SECONDS.format(instant);
  }

  /** A calendar date with no time zone, such as a date of birth. */
  public String day(LocalDate date) {
    return date == null ? "" : CALENDAR_DATE.format(date);
  }

  /** "Started", "Task completed" and so on, for an Action type such as TASK-COMPLETED. */
  public String actionType(String type) {
    if (type == null || type.isBlank()) {
      return "";
    }
    String words = type.replace('-', ' ').replace('_', ' ').toLowerCase(Locale.ROOT);
    return Character.toUpperCase(words.charAt(0)) + words.substring(1);
  }

  public String gender(String gender) {
    if (gender == null || gender.isBlank()) {
      return "Not recorded";
    }
    return gender.charAt(0) + gender.substring(1).toLowerCase(Locale.ROOT);
  }

  public String age(Integer age) {
    return age == null ? "Unknown" : age.toString();
  }

  /** A rate from 0 to 1 as a whole percentage, such as "96%". A dash when there is none. */
  public String percent(Double rate) {
    return rate == null ? "–" : Math.round(rate * 100) + "%";
  }

  /**
   * A duration in seconds, to one decimal place: hours under two days, days after that. A dash
   * when there is none.
   */
  public String duration(Long seconds) {
    if (seconds == null) {
      return "–";
    }
    double hours = seconds / 3600.0;
    if (hours < 48) {
      return String.format(Locale.UK, "%.1f hours", hours);
    }
    return String.format(Locale.UK, "%.1f days", hours / 24);
  }

  /** A week's tooltip, such as "2025-W10 · week of 3 Mar 2025: 2 patients". */
  public String week(String isoWeek, LocalDate weekStart, long patients) {
    return isoWeek + " · week of " + day(weekStart) + ": " + patients(patients);
  }

  /** "1 patient", "2 patients". */
  public String patients(long count) {
    return count + (count == 1 ? " patient" : " patients");
  }

  public String label(Enum<?> value) {
    String name = value.name();
    return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
  }
}
