package uk.bit1.engagement.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.bit1.engagement.London;
import uk.bit1.engagement.dashboard.WeeklyActive.Week;
import uk.bit1.engagement.dashboard.WeeklyActiveQuery.WeekRange;

class WeeklyActiveTest {

  private static final LocalDate MON_3_MAR = LocalDate.parse("2025-03-03");
  private static final LocalDate MON_10_MAR = LocalDate.parse("2025-03-10");
  private static final LocalDate MON_17_MAR = LocalDate.parse("2025-03-17");

  @Test
  void emptyWeeksAppearAsZero() {
    WeeklyActive weekly =
        WeeklyActive.of(new WeekRange(MON_3_MAR, MON_17_MAR), Map.of(MON_3_MAR, 4L, MON_17_MAR, 1L));

    assertThat(weekly.weeks())
        .containsExactly(
            new Week(MON_3_MAR, "2025-W10", 4),
            new Week(MON_10_MAR, "2025-W11", 0),
            new Week(MON_17_MAR, "2025-W12", 1));
    assertThat(weekly.from()).isEqualTo(MON_3_MAR);
    assertThat(weekly.to()).isEqualTo(MON_17_MAR);
    assertThat(weekly.peak()).isEqualTo(4);
  }

  @Test
  void aRangeWithNoActionsIsAllZero() {
    WeeklyActive weekly = WeeklyActive.of(new WeekRange(MON_3_MAR, MON_10_MAR), Map.of());

    assertThat(weekly.weeks()).extracting(Week::activePatients).containsExactly(0L, 0L);
    assertThat(weekly.peak()).isZero();
  }

  @Test
  void anEmptyDatasetHasNoWeeks() {
    WeeklyActive weekly = WeeklyActive.empty();

    assertThat(weekly.weeks()).isEmpty();
    assertThat(weekly.from()).isNull();
    assertThat(weekly.peak()).isZero();
  }

  @Test
  void isoWeekLabelsUseTheWeekBasedYear() {
    // Monday 29 Dec 2025 starts ISO week 1 of 2026.
    assertThat(WeeklyActive.isoWeek(LocalDate.parse("2025-12-29"))).isEqualTo("2026-W01");
    assertThat(WeeklyActive.isoWeek(LocalDate.parse("2024-12-30"))).isEqualTo("2025-W01");
  }

  @Test
  void anActionJustAfterMidnightUtcDuringBstLandsInTheNextLondonDayAndWeek() {
    // Sunday 29 June 2025, 23:30 UTC, is 00:30 on Monday 30 June in London (BST).
    Instant recorded = Instant.parse("2025-06-29T23:30:00Z");

    assertThat(London.dateOf(recorded)).isEqualTo(LocalDate.parse("2025-06-30"));
    assertThat(London.weekOf(recorded)).isEqualTo(LocalDate.parse("2025-06-30"));
  }

  @Test
  void inWinterLondonIsUtcSoTheSameMomentStaysInItsWeek() {
    // Sunday 5 January 2025, 23:30 UTC, is still Sunday in London (GMT).
    Instant recorded = Instant.parse("2025-01-05T23:30:00Z");

    assertThat(London.dateOf(recorded)).isEqualTo(LocalDate.parse("2025-01-05"));
    assertThat(London.weekOf(recorded)).isEqualTo(LocalDate.parse("2024-12-30"));
  }

  @Test
  void weekBoundsAreLondonMidnights() {
    // The week starting Monday 30 June 2025 begins at 23:00 UTC on the Sunday.
    assertThat(London.startOf(LocalDate.parse("2025-06-30")))
        .isEqualTo(Instant.parse("2025-06-29T23:00:00Z"));
  }
}
