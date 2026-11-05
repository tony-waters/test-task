package uk.bit1.engagement.action;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SuspectTimestampsTest {

  private static final Instant EXTRACTION_STARTED = Instant.parse("2026-01-16T13:00:37.299144Z");
  private static final Optional<Instant> THRESHOLD =
      Optional.of(SuspectTimestamps.thresholdFor(EXTRACTION_STARTED));

  @Test
  void anActionRecordedAfterExtractionBeganIsSuspect() {
    assertThat(SuspectTimestamps.isSuspect(EXTRACTION_STARTED.plusSeconds(4), THRESHOLD)).isTrue();
  }

  @Test
  void soIsOneRecordedUpToOneSecondBefore() {
    assertThat(SuspectTimestamps.isSuspect(EXTRACTION_STARTED.minusSeconds(1), THRESHOLD)).isTrue();
    assertThat(SuspectTimestamps.isSuspect(EXTRACTION_STARTED.minusMillis(1001), THRESHOLD))
        .isFalse();
  }

  @Test
  void anOrdinaryActionIsNot() {
    assertThat(SuspectTimestamps.isSuspect(EXTRACTION_STARTED.minusSeconds(3600), THRESHOLD))
        .isFalse();
  }

  @Test
  void nothingIsSuspectWithoutAThresholdOrATime() {
    assertThat(SuspectTimestamps.isSuspect(EXTRACTION_STARTED, Optional.empty())).isFalse();
    assertThat(SuspectTimestamps.isSuspect(null, THRESHOLD)).isFalse();
  }
}
