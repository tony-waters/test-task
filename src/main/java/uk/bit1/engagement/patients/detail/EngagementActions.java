package uk.bit1.engagement.patients.detail;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uk.bit1.engagement.core.Action;

/** The Engagement summary's query. */
interface EngagementActions extends Repository<Action, Long> {

  /** The Engagement summary's input: one row per (Module, Topic, Action type). */
  @Query(
      """
      select new uk.bit1.engagement.patients.detail.EngagementRow(
                 a.module, a.topic, a.actionType, count(a), min(a.recordedAt), max(a.recordedAt))
        from Action a
       where a.patient.id = :patientId
       group by a.module, a.topic, a.actionType
      """)
  List<EngagementRow> engagement(long patientId);
}
