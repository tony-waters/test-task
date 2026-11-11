package uk.bit1.engagement.patients.detail;

import java.time.Clock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.core.Age;
import uk.bit1.engagement.core.Patient;
import uk.bit1.engagement.core.PatientRepository;
import uk.bit1.engagement.patients.quality.DataQualityFlag;
import uk.bit1.engagement.patients.quality.SuspectTimestamps;
import uk.bit1.engagement.shared.London;
import uk.bit1.engagement.shared.NotFoundException;

/** One Patient's detail: identity, Status, data-quality flags and Engagement summary. */
@Service
@Transactional(readOnly = true)
class PatientDetails {

  private final PatientRepository patients;
  private final EngagementActions actions;
  private final SuspectTimestamps suspectTimestamps;
  private final Clock clock;

  PatientDetails(
      PatientRepository patients,
      EngagementActions actions,
      SuspectTimestamps suspectTimestamps,
      Clock clock) {
    this.patients = patients;
    this.actions = actions;
    this.suspectTimestamps = suspectTimestamps;
    this.clock = clock;
  }

  /**
   * One Patient's detail and Engagement summary. Three statements.
   *
   * @throws NotFoundException when no Patient has that UUID
   */
  public PatientDetail detail(UUID id) {
    Patient p = patients.getByUuid(id);
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
}
