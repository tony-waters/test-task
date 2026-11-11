package uk.bit1.engagement.application.dashboard.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StatusCountsTest {

  private static final Instant INVITED = Instant.parse("2025-01-06T10:00:00Z");
  private static final Instant LATER = Instant.parse("2025-02-06T10:00:00Z");
  private static final Instant LATER_STILL = Instant.parse("2025-03-06T10:00:00Z");

  @Test
  void bucketsAreMutuallyExclusiveAndAddUpToTheTotal() {
    StatusCounts counts =
        StatusCounts.from(
            List.of(
                new Journey(INVITED, null, null),
                new Journey(INVITED, LATER, null),
                new Journey(INVITED, LATER, null),
                new Journey(INVITED, LATER, LATER_STILL)));

    assertThat(counts).isEqualTo(new StatusCounts(1, 2, 1, 4));
  }

  @Test
  void aSkippedRegistrationIsDischarged() {
    StatusCounts counts = StatusCounts.from(List.of(new Journey(INVITED, null, LATER)));

    assertThat(counts).isEqualTo(new StatusCounts(0, 0, 1, 1));
  }

  @Test
  void anEmptyPopulationIsAllZero() {
    assertThat(StatusCounts.from(List.of())).isEqualTo(new StatusCounts(0, 0, 0, 0));
  }
}
