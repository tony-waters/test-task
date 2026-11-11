package uk.bit1.engagement.patients.timeline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.core.Action;
import uk.bit1.engagement.core.Patient;
import uk.bit1.engagement.core.PatientRepository;
import uk.bit1.engagement.patients.quality.SuspectTimestamps;
import uk.bit1.engagement.shared.NotFoundException;

/**
 * Pages a Patient's Actions, newest first, with keyset paging on (recorded time, id). A page is one
 * statement however deep it is, and Actions loaded between requests cannot shift a page.
 */
@Service
@Transactional(readOnly = true)
public class Timeline {

  public static final int PAGE_SIZE = 50;

  private final PatientRepository patients;
  private final TimelineActions actions;
  private final SuspectTimestamps suspectTimestamps;

  Timeline(
      PatientRepository patients, TimelineActions actions, SuspectTimestamps suspectTimestamps) {
    this.patients = patients;
    this.actions = actions;
    this.suspectTimestamps = suspectTimestamps;
  }

  /**
   * One page of a Patient's timeline. Three statements.
   *
   * @param cursor where the previous page ended, or null for the newest page
   * @throws NotFoundException when no Patient has that UUID
   */
  public TimelinePage page(UUID id, TimelineCursor cursor) {
    Patient patient = patients.getByUuid(id);
    return page(patient, cursor, suspectTimestamps.threshold());
  }

  private TimelinePage page(
      Patient patient, TimelineCursor cursor, Optional<Instant> suspectThreshold) {
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
