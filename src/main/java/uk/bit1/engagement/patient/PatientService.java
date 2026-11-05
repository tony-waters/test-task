package uk.bit1.engagement.patient;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.London;
import uk.bit1.engagement.action.ActionRepository;
import uk.bit1.engagement.action.EngagementSummary;
import uk.bit1.engagement.action.SuspectTimestamps;
import uk.bit1.engagement.action.Timeline;
import uk.bit1.engagement.action.TimelineCursor;
import uk.bit1.engagement.action.TimelinePage;
import uk.bit1.engagement.web.NotFoundException;

/** What the patient pages and the API share: the list, one Patient's detail, and their timeline. */
@Service
@Transactional(readOnly = true)
public class PatientService {

  private final PatientRepository patients;
  private final ActionRepository actions;
  private final SuspectTimestamps suspectTimestamps;
  private final Timeline timeline;
  private final Clock clock;

  public PatientService(
      PatientRepository patients,
      ActionRepository actions,
      SuspectTimestamps suspectTimestamps,
      Timeline timeline,
      Clock clock) {
    this.patients = patients;
    this.actions = actions;
    this.suspectTimestamps = suspectTimestamps;
    this.timeline = timeline;
    this.clock = clock;
  }

  /** One page of the patient list. Two statements whatever the page size. */
  public PatientPage find(PatientListQuery query) {
    return patients.search(query);
  }

  /**
   * One Patient's detail and Engagement summary. Three statements.
   *
   * @throws NotFoundException when no Patient has that UUID
   */
  public PatientDetail detail(UUID id) {
    Patient p = find(id);
    EngagementSummary engagement = EngagementSummary.from(actions.engagement(p.getId()));
    Set<DataQualityFlag> flags = EnumSet.noneOf(DataQualityFlag.class);
    if (p.getDischargedAt() != null && p.getRegisteredAt() == null) {
      flags.add(DataQualityFlag.SKIPPED_REGISTRATION);
    }
    // The latest Action is suspect whenever any is: suspect means "late enough".
    if (SuspectTimestamps.isSuspect(engagement.lastActionAt(), suspectTimestamps.threshold())) {
      flags.add(DataQualityFlag.SUSPECT_TIMESTAMPS);
    }
    return new PatientDetail(
        p.getUuid(),
        p.getTitle(),
        p.getGivenName(),
        p.getFamilyName(),
        p.getGender(),
        p.getDateOfBirth(),
        Age.inYears(p.getDateOfBirth(), London.today(clock)),
        p.getNhsNumber(),
        p.getHospitalId(),
        p.getStatus(),
        p.getInvitedAt(),
        p.getRegisteredAt(),
        p.getDischargedAt(),
        Collections.unmodifiableSet(flags), // EnumSet: declaration order in JSON
        engagement);
  }

  /**
   * One page of a Patient's timeline. Three statements.
   *
   * @throws NotFoundException when no Patient has that UUID
   */
  public TimelinePage timeline(UUID id, TimelineCursor cursor) {
    Patient p = find(id);
    Optional<Instant> threshold = suspectTimestamps.threshold();
    return timeline.page(p, cursor, threshold);
  }

  private Patient find(UUID id) {
    return patients.findByUuid(id).orElseThrow(() -> new NotFoundException("No patient has that ID."));
  }
}
