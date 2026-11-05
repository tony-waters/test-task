package uk.bit1.engagement.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import uk.bit1.engagement.dashboard.WeeklyActiveQuery.DateSpan;
import uk.bit1.engagement.dashboard.WeeklyActiveQuery.WeekRange;
import uk.bit1.engagement.web.InvalidParametersException;
import uk.bit1.engagement.web.ParameterError;

class WeeklyActiveQueryTest {

  /** First Action on Wednesday 5 March 2025, last on Friday 16 January 2026. */
  private static final Optional<DateSpan> ACTIONS =
      Optional.of(new DateSpan(LocalDate.parse("2025-03-05"), LocalDate.parse("2026-01-16")));

  private static LocalDate d(String date) {
    return LocalDate.parse(date);
  }

  @Test
  void theDefaultIsTheWeekOfTheFirstActionToTheWeekOfTheLast() {
    Optional<WeekRange> range = WeeklyActiveQuery.parse(null, " ").resolve(ACTIONS);

    assertThat(range).contains(new WeekRange(d("2025-03-03"), d("2026-01-12")));
    assertThat(range.get().weeks()).isEqualTo(46);
  }

  @Test
  void chosenDatesSnapToTheirWeeks() {
    Optional<WeekRange> range = WeeklyActiveQuery.parse("2025-03-09", "2025-03-10").resolve(ACTIONS);

    // Sunday 9 March is in the week of 3 March; Monday 10 March starts the next.
    assertThat(range).contains(new WeekRange(d("2025-03-03"), d("2025-03-10")));
  }

  @Test
  void oneChosenEndKeepsTheOtherDefault() {
    assertThat(WeeklyActiveQuery.parse("2025-06-01", null).resolve(ACTIONS))
        .contains(new WeekRange(d("2025-05-26"), d("2026-01-12")));
    assertThat(WeeklyActiveQuery.parse(null, "2025-06-01").resolve(ACTIONS))
        .contains(new WeekRange(d("2025-03-03"), d("2025-05-26")));
  }

  @Test
  void aChosenEndBeyondTheActionsNeverRunsBackwards() {
    assertThat(WeeklyActiveQuery.parse("2026-06-01", null).resolve(ACTIONS))
        .contains(new WeekRange(d("2026-06-01"), d("2026-06-01")));
    assertThat(WeeklyActiveQuery.parse(null, "2024-01-01").resolve(ACTIONS))
        .contains(new WeekRange(d("2024-01-01"), d("2024-01-01")));
  }

  @Test
  void withNoActionsTheDefaultIsNothing() {
    assertThat(WeeklyActiveQuery.defaults().resolve(Optional.empty())).isEmpty();
  }

  @Test
  void withNoActionsAChosenRangeStillShows() {
    assertThat(WeeklyActiveQuery.parse("2025-03-05", "2025-03-12").resolve(Optional.empty()))
        .contains(new WeekRange(d("2025-03-03"), d("2025-03-10")));
    assertThat(WeeklyActiveQuery.parse(null, "2025-03-12").resolve(Optional.empty()))
        .contains(new WeekRange(d("2025-03-10"), d("2025-03-10")));
  }

  @Test
  void rejectsDatesThatAreNotIsoDates() {
    assertThatThrownBy(() -> WeeklyActiveQuery.parse("05/03/2025", "2025-02-30"))
        .isInstanceOfSatisfying(
            InvalidParametersException.class,
            e ->
                assertThat(e.getErrors())
                    .extracting(ParameterError::parameter)
                    .containsExactly("from", "to"));
  }

  @Test
  void rejectsARangeThatRunsBackwards() {
    assertThatThrownBy(() -> WeeklyActiveQuery.parse("2025-03-10", "2025-03-09"))
        .isInstanceOfSatisfying(
            InvalidParametersException.class,
            e ->
                assertThat(e.getErrors())
                    .containsExactly(new ParameterError("to", "must not be before from")));
  }

  @Test
  void aRangeInsideOneWeekIsFine() {
    assertThat(WeeklyActiveQuery.parse("2025-03-04", "2025-03-04").resolve(ACTIONS))
        .contains(new WeekRange(d("2025-03-03"), d("2025-03-03")));
  }

  @Test
  void rejectsAChosenRangeWiderThanTheCap() {
    assertThatThrownBy(() -> WeeklyActiveQuery.parse("2000-01-01", "2025-01-01"))
        .isInstanceOfSatisfying(
            InvalidParametersException.class,
            e -> assertThat(e.getErrors()).extracting(ParameterError::parameter).containsExactly("to"));
  }

  /** Actions from Monday 4 Jan 2010 to Friday 16 Jan 2026: far more than the cap. */
  private static final Optional<DateSpan> SIXTEEN_YEARS =
      Optional.of(new DateSpan(LocalDate.parse("2010-01-04"), LocalDate.parse("2026-01-16")));

  @Test
  void aDefaultRangeWiderThanTheCapKeepsTheMostRecentWeeks() {
    Optional<WeekRange> range = WeeklyActiveQuery.defaults().resolve(SIXTEEN_YEARS);

    assertThat(range).contains(new WeekRange(d("2016-02-01"), d("2026-01-12")));
    assertThat(range.get().weeks()).isEqualTo(WeeklyActiveQuery.MAX_WEEKS);
  }

  @Test
  void aFilledInEndIsPulledInToFitTheCap() {
    // Only "from" chosen: "to" is pulled back to 520 weeks after it.
    assertThat(WeeklyActiveQuery.parse("1900-01-01", null).resolve(ACTIONS))
        .contains(new WeekRange(d("1900-01-01"), d("1909-12-13")));
    // Only "to" chosen: "from" is pulled forward to 520 weeks before it.
    assertThat(WeeklyActiveQuery.parse(null, "2026-01-16").resolve(SIXTEEN_YEARS))
        .contains(new WeekRange(d("2016-02-01"), d("2026-01-12")));
  }
}
