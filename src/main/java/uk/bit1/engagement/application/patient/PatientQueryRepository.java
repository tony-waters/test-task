package uk.bit1.engagement.application.patient;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uk.bit1.engagement.application.patient.query.EngagementRow;
import uk.bit1.engagement.domain.action.Action;
import uk.bit1.engagement.domain.patient.Patient;

/**
 * The read side's queries over Patients and their Actions: the list (through the {@link
 * PatientSearch} fragment), the Engagement summary the timeline and when extraction began. Entity lookups stay in the
 * domain repositories.
 */
public interface PatientQueryRepository extends Repository<Patient, Long>, PatientSearch {

  /** The Engagement summary's input: one row per (Module, Topic, Action type). */
  @Query(
      """
      select new uk.bit1.engagement.application.patient.query.EngagementRow(
                 a.module, a.topic, a.actionType, count(a), min(a.recordedAt), max(a.recordedAt))
        from Action a
       where a.patient.id = :patientId
       group by a.module, a.topic, a.actionType
      """)
  List<EngagementRow> engagement(long patientId);

  /** The first timeline page: newest first, ties broken by id so the order is total. */
  @Query(
      """
      select a from Action a
       where a.patient.id = :patientId
       order by a.recordedAt desc, a.id desc
      """)
  List<Action> newestActions(long patientId, Limit limit);

  /** The timeline page after a cursor: strictly older in (recorded time, id) order. */
  @Query(
      """
      select a from Action a
       where a.patient.id = :patientId
         and (a.recordedAt < :recordedAt or (a.recordedAt = :recordedAt and a.id < :id))
       order by a.recordedAt desc, a.id desc
      """)
  List<Action> actionsOlderThan(long patientId, Instant recordedAt, long id, Limit limit);

  /**
   * When extraction began: the earliest {@code sourceCreatedAt} in either table, or empty when
   * nothing is loaded. One statement. It scans both tables: there is no index on {@code
   * source_created_at} yet.
   */
  @Query(
      """
      select least(min(p.sourceCreatedAt), (select min(a.sourceCreatedAt) from Action a))
        from Patient p
      """)
  Optional<Instant> extractionStarted();
}
