package uk.bit1.engagement.action;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import uk.bit1.engagement.patient.Patient;

/**
 * Pages a Patient's Actions, newest first, with keyset paging on (recorded time, id). A page is one
 * statement however deep it is, and Actions loaded between requests cannot shift a page.
 */
@Component
public class Timeline {

  public static final int PAGE_SIZE = 50;

  private final ActionRepository actions;

  public Timeline(ActionRepository actions) {
    this.actions = actions;
  }

  /**
   * @param cursor where the previous page ended, or null for the newest page
   * @param suspectThreshold from {@link SuspectTimestamps#threshold()}
   */
  public TimelinePage page(Patient patient, TimelineCursor cursor, Optional<Instant> suspectThreshold) {
    Limit limit = Limit.of(PAGE_SIZE + 1); // one extra row says whether there is a next page
    List<Action> rows =
        cursor == null
            ? actions.newest(patient.getId(), limit)
            : actions.olderThan(patient.getId(), cursor.recordedAt(), cursor.id(), limit);
    boolean more = rows.size() > PAGE_SIZE;
    List<Action> page = more ? rows.subList(0, PAGE_SIZE) : rows;
    String next = null;
    if (more) {
      Action last = page.getLast();
      next = new TimelineCursor(last.getRecordedAt(), last.getId()).encode();
    }
    Instant dischargedAt = patient.getDischargedAt();
    List<TimelineEntry> entries =
        page.stream()
            .map(
                a ->
                    new TimelineEntry(
                        a.getUuid(),
                        a.getRecordedAt(),
                        a.getModule(),
                        a.getTopic(),
                        a.getActionType(),
                        dischargedAt != null && a.getRecordedAt().isAfter(dischargedAt),
                        SuspectTimestamps.isSuspect(a.getRecordedAt(), suspectThreshold)))
            .toList();
    return new TimelinePage(entries, PAGE_SIZE, next);
  }
}
