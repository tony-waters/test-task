package uk.bit1.engagement.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import uk.bit1.engagement.shared.London;

class AgeTest {

  private static final LocalDate TODAY = LocalDate.of(2026, 11, 5);

  @Test
  void unknownDateOfBirthGivesUnknownAge() {
    assertThat(Age.inYears(null, TODAY)).isNull();
  }

  @Test
  void birthdayTodayCountsTheNewYear() {
    assertThat(Age.inYears(LocalDate.of(2000, 11, 5), TODAY)).isEqualTo(26);
  }

  @Test
  void birthdayTomorrowDoesNot() {
    assertThat(Age.inYears(LocalDate.of(2000, 11, 6), TODAY)).isEqualTo(25);
  }

  @Test
  void aLeapDayBirthdayCountsFromTheFirstOfMarchInOrdinaryYears() {
    LocalDate leapDay = LocalDate.of(2000, 2, 29);

    assertThat(Age.inYears(leapDay, LocalDate.of(2027, 2, 28))).isEqualTo(26);
    assertThat(Age.inYears(leapDay, LocalDate.of(2027, 3, 1))).isEqualTo(27);
  }

  @Test
  void aLeapDayBirthdayCountsOnTheDayInLeapYears() {
    LocalDate leapDay = LocalDate.of(2000, 2, 29);

    assertThat(Age.inYears(leapDay, LocalDate.of(2028, 2, 28))).isEqualTo(27);
    assertThat(Age.inYears(leapDay, LocalDate.of(2028, 2, 29))).isEqualTo(28);
  }

  @Test
  void todayIsTheLondonDateNotTheUtcOne() {
    // 23:30 UTC on 30 June is 00:30 on 1 July in London (BST).
    Clock lateEvening = Clock.fixed(Instant.parse("2026-06-30T23:30:00Z"), ZoneOffset.UTC);

    assertThat(Age.inYears(LocalDate.of(2000, 7, 1), London.today(lateEvening))).isEqualTo(26);
  }
}
