package uk.bit1.engagement.action;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

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
@Component
public class SuspectTimestamps {

  /** How far before extraction began a recorded time still counts as suspect. */
  static final Duration MARGIN = Duration.ofSeconds(1);

  private final EntityManager em;

  public SuspectTimestamps(EntityManager em) {
    this.em = em;
  }

  /**
   * The earliest recorded time that is suspect, or empty when nothing is loaded. One statement.
   * It scans both tables: there is no index on {@code source_created_at} yet.
   */
  public Optional<Instant> threshold() {
    Object result =
        em.createNativeQuery(
                "select least((select min(source_created_at) from patient),"
                    + " (select min(source_created_at) from action))")
            .getSingleResult();
    return Optional.ofNullable(toInstant(result)).map(SuspectTimestamps::thresholdFor);
  }

  /** The suspect threshold for a given extraction start. */
  static Instant thresholdFor(Instant extractionStarted) {
    return extractionStarted.minus(MARGIN);
  }

  /** Whether {@code recordedAt} is suspect under {@code threshold}; never when there is none. */
  public static boolean isSuspect(Instant recordedAt, Optional<Instant> threshold) {
    return recordedAt != null && threshold.map(t -> !recordedAt.isBefore(t)).orElse(false);
  }

  private static Instant toInstant(Object value) {
    return switch (value) {
      case null -> null;
      case Instant i -> i;
      case OffsetDateTime o -> o.toInstant();
      case Timestamp t -> t.toInstant();
      default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
    };
  }
}
