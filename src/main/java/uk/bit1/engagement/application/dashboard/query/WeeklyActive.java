package uk.bit1.engagement.application.dashboard.query;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import uk.bit1.engagement.application.dashboard.query.WeeklyActiveQuery.WeekRange;

/**
 * How many Patients recorded at least one Action in each London ISO week. Every Action counts,
 * including post-discharge Actions and suspect timestamps. Weeks with no Actions are 0.
 *
 * @param from the Monday of the first week, or null when there is nothing to show
 * @param to the Monday of the last week, or null when there is nothing to show
 * @param weeks one entry per week, oldest first
 */
public record WeeklyActive(LocalDate from, LocalDate to, List<Week> weeks) {

  /**
   * @param weekStart the week's Monday
   * @param isoWeek the ISO week, such as {@code 2025-W09}
   */
  public record Week(LocalDate weekStart, String isoWeek, long activePatients) {}

  public static WeeklyActive empty() {
    return new WeeklyActive(null, null, List.of());
  }

  /**
   * Every week of {@code range}, taking counts from {@code activeByWeek} (keyed by Monday) and 0
   * for the weeks it lacks.
   */
  public static WeeklyActive of(WeekRange range, Map<LocalDate, Long> activeByWeek) {
    List<Week> weeks = new ArrayList<>();
    for (LocalDate monday = range.firstWeek();
        !monday.isAfter(range.lastWeek());
        monday = monday.plusWeeks(1)) {
      weeks.add(new Week(monday, isoWeek(monday), activeByWeek.getOrDefault(monday, 0L)));
    }
    return new WeeklyActive(range.firstWeek(), range.lastWeek(), List.copyOf(weeks));
  }

  /** The busiest week's count, for scaling a chart. 0 when every week is empty. */
  public long peak() {
    return weeks.stream().mapToLong(Week::activePatients).max().orElse(0);
  }

  static String isoWeek(LocalDate date) {
    return "%d-W%02d"
        .formatted(date.get(IsoFields.WEEK_BASED_YEAR), date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
  }
}
