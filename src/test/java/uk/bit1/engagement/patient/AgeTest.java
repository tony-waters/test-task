package uk.bit1.engagement.patient;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

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
}
