package uk.bit1.engagement.application.patient;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.application.common.NotFoundException;
import uk.bit1.engagement.application.patient.query.EngagementSummary;
import uk.bit1.engagement.application.patient.query.PatientDetail;
import uk.bit1.engagement.application.patient.query.PatientListQuery;
import uk.bit1.engagement.application.patient.query.PatientPage;
import uk.bit1.engagement.application.patient.query.PatientSummary;
import uk.bit1.engagement.application.patient.query.TimelineCursor;
import uk.bit1.engagement.application.patient.query.TimelinePage;
import uk.bit1.engagement.domain.action.SuspectTimestamps;
import uk.bit1.engagement.domain.common.London;
import uk.bit1.engagement.domain.patient.Age;
import uk.bit1.engagement.domain.patient.DataQualityFlag;
import uk.bit1.engagement.domain.patient.Patient;
import uk.bit1.engagement.domain.patient.PatientRepository;

/** What the patient pages and the API share: the list, one Patient's detail, and their timeline. */
@Service
@Transactional(readOnly = true)
public class PatientQueryService {

  private final PatientRepository patients;
  private final PatientQueryRepository queries;
  private final Timeline timeline;
  private final Clock clock;

  public PatientQueryService(
      PatientRepository patients,
      PatientQueryRepository queries,
      Timeline timeline,
      Clock clock) {
    this.patients = patients;
    this.queries = queries;
    this.timeline = timeline;
    this.clock = clock;
  }

  /** One page of the patient list. Two statements whatever the page size. */
  public PatientPage find(PatientListQuery query) {
    return queries.search(query);
  }

  /**
   * Every Patient matching the query's search and filters, in its sort order, ignoring its page.
   * Read lazily through a database cursor, so the caller must hold a transaction open while it
   * reads and must close the stream.
   */
  public Stream<PatientSummary> stream(PatientListQuery query, int fetchSize) {
    return queries.stream(query, fetchSize);
  }

  /**
   * One Patient's detail and Engagement summary. Three statements.
   *
   * @throws NotFoundException when no Patient has that UUID
   */
  public PatientDetail detail(UUID id) {
    Patient p = find(id);
    EngagementSummary engagement = EngagementSummary.from(queries.engagement(p.getId()));
    Set<DataQualityFlag> flags = EnumSet.noneOf(DataQualityFlag.class);
    if (p.getDischargedAt() != null && p.getRegisteredAt() == null) {
      flags.add(DataQualityFlag.SKIPPED_REGISTRATION);
    }
    // The latest Action is suspect whenever any is: suspect means "late enough".
    if (SuspectTimestamps.isSuspect(engagement.lastActionAt(), suspectThreshold())) {
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
    Optional<Instant> threshold = suspectThreshold();
    return timeline.page(p, cursor, threshold);
  }

  private Optional<Instant> suspectThreshold() {
    return SuspectTimestamps.threshold(queries.extractionStarted());
  }

  private Patient find(UUID id) {
    return patients.findByUuid(id).orElseThrow(() -> new NotFoundException("No patient has that ID."));
  }
}
