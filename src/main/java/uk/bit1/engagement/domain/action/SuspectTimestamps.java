package uk.bit1.engagement.domain.action;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Decides which Actions have a suspect timestamp: recorded at, or within one second before, the
 * moment extraction began.
 *
 * <p>Extraction began at the earliest {@code source_created_at} in either table. The source
 * stamps every row as it writes it, so one dump's rows span a few seconds. Comparing each Action
 * with its own row's stamp would make the rule depend on the order rows were written. On the
 * supplied dump this flags 47 Actions, all recorded during the extraction. The nearest Action
 * before them is about an hour earlier, so the one-second margin changes nothing there. It is kept
 * because the clamped timestamps sit a few milliseconds either side of "now".
 */
public final class SuspectTimestamps {

  /** How far before extraction began a recorded time still counts as suspect. */
  static final Duration MARGIN = Duration.ofSeconds(1);

  private SuspectTimestamps() {}

  /**
   * The earliest recorded time that is suspect, given when extraction began; empty when nothing is
   * loaded.
   */
  public static Optional<Instant> threshold(Optional<Instant> extractionStarted) {
    return extractionStarted.map(SuspectTimestamps::thresholdFor);
  }

  /** The suspect threshold for a given extraction start. */
  static Instant thresholdFor(Instant extractionStarted) {
    return extractionStarted.minus(MARGIN);
  }

  /** Whether {@code recordedAt} is suspect under {@code threshold}; never when there is none. */
  public static boolean isSuspect(Instant recordedAt, Optional<Instant> threshold) {
    return recordedAt != null && threshold.map(t -> !recordedAt.isBefore(t)).orElse(false);
  }
}
