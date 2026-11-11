package uk.bit1.engagement.application.dashboard.query;

import java.time.Instant;
import java.util.Optional;
import uk.bit1.engagement.application.dashboard.query.WeeklyActiveQuery.DateSpan;
import uk.bit1.engagement.domain.common.London;

/** The recorded times of the first and last Action; both null when there are none. */
public record RecordedSpan(Instant first, Instant last) {

  /** The London dates of the span, or empty when there are no Actions. */
  public Optional<DateSpan> londonDates() {
    if (first == null) {
      return Optional.empty();
    }
    return Optional.of(new DateSpan(London.dateOf(first), London.dateOf(last)));
  }
}
