package uk.bit1.engagement.dashboard;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import uk.bit1.engagement.shared.InvalidParametersException;
import uk.bit1.engagement.shared.London;
import uk.bit1.engagement.shared.ParameterError;

/**
 * A validated weekly-active request. The series covers every London ISO week from the one holding
 * {@code from} to the one holding {@code to}. A missing end defaults to the week of the first or
 * last Action, within {@link #MAX_WEEKS} of the other end.
 *
 * @param from a London date in the first week, or null for the week of the first Action
 * @param to a London date in the last week, or null for the week of the last Action
 */
public record WeeklyActiveQuery(LocalDate from, LocalDate to) {

  /** About ten years. Stops a wide range from building a huge series of zeros. */
  public static final int MAX_WEEKS = 520;

  public static WeeklyActiveQuery defaults() {
    return new WeeklyActiveQuery(null, null);
  }

  /**
   * Parses raw request parameters. Absent or blank values take their defaults.
   *
   * @throws InvalidParametersException listing every invalid parameter
   */
  public static WeeklyActiveQuery parse(String from, String to) {
    List<ParameterError> errors = new ArrayList<>();
    LocalDate fromDate = parseDate("from", from, errors);
    LocalDate toDate = parseDate("to", to, errors);
    if (fromDate != null && toDate != null) {
      if (fromDate.isAfter(toDate)) {
        errors.add(new ParameterError("to", "must not be before from"));
      } else if (weeks(fromDate, toDate) > MAX_WEEKS) {
        errors.add(tooWide("to"));
      }
    }
    if (!errors.isEmpty()) {
      throw new InvalidParametersException(errors);
    }
    return new WeeklyActiveQuery(fromDate, toDate);
  }

  /**
   * The first and last week (as their Mondays) once the missing ends are filled from the London
   * dates of the first and last Action. Empty when an end is missing and there are no Actions.
   *
   * <p>A missing end never makes the range run backwards: given only a {@code from} after the
   * last Action, the series is that one week. Nor does it make the range wider than {@link
   * #MAX_WEEKS}: a filled-in end is pulled in to fit, keeping the most recent weeks when both are
   * filled in. Only a range the user chose in full can be too wide, and {@link #parse} rejects
   * that, so this never fails.
   */
  public Optional<WeekRange> resolve(Optional<DateSpan> actions) {
    LocalDate first = from;
    LocalDate last = to;
    if (actions.isPresent()) {
      DateSpan span = actions.get();
      if (first == null) {
        first = last == null ? span.first() : min(span.first(), last);
      }
      if (last == null) {
        last = max(span.last(), first);
      }
    } else if (first == null && last == null) {
      return Optional.empty();
    } else {
      first = first == null ? last : first;
      last = last == null ? first : last;
    }
    LocalDate firstWeek = London.weekStart(first);
    LocalDate lastWeek = London.weekStart(last);
    if (new WeekRange(firstWeek, lastWeek).weeks() > MAX_WEEKS) {
      if (from == null) {
        firstWeek = lastWeek.minusWeeks(MAX_WEEKS - 1);
      } else {
        lastWeek = firstWeek.plusWeeks(MAX_WEEKS - 1);
      }
    }
    return Optional.of(new WeekRange(firstWeek, lastWeek));
  }

  /** The London dates of the first and last Action. */
  public record DateSpan(LocalDate first, LocalDate last) {}

  /** A run of whole ISO weeks, each given by its Monday. */
  public record WeekRange(LocalDate firstWeek, LocalDate lastWeek) {

    public long weeks() {
      return ChronoUnit.WEEKS.between(firstWeek, lastWeek) + 1;
    }
  }

  private static long weeks(LocalDate from, LocalDate to) {
    return new WeekRange(London.weekStart(from), London.weekStart(to)).weeks();
  }

  private static ParameterError tooWide(String parameter) {
    return new ParameterError(parameter, "the range must cover at most " + MAX_WEEKS + " weeks");
  }

  private static LocalDate min(LocalDate a, LocalDate b) {
    return a.isBefore(b) ? a : b;
  }

  private static LocalDate max(LocalDate a, LocalDate b) {
    return a.isAfter(b) ? a : b;
  }

  private static LocalDate parseDate(String parameter, String value, List<ParameterError> errors) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      errors.add(new ParameterError(parameter, "must be a date in the form yyyy-mm-dd"));
      return null;
    }
  }
}
