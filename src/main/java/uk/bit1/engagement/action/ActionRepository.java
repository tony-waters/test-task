package uk.bit1.engagement.action;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Reads Actions. Only the loader writes them. */
public interface ActionRepository extends JpaRepository<Action, Long> {

  /** The Engagement summary's input: one row per (Module, Topic, Action type). */
  @Query(
      """
      select new uk.bit1.engagement.action.EngagementRow(
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
  List<Action> newest(long patientId, Limit limit);

  /** The timeline page after a cursor: strictly older in (recorded time, id) order. */
  @Query(
      """
      select a from Action a
       where a.patient.id = :patientId
         and (a.recordedAt < :recordedAt or (a.recordedAt = :recordedAt and a.id < :id))
       order by a.recordedAt desc, a.id desc
      """)
  List<Action> olderThan(long patientId, Instant recordedAt, long id, Limit limit);
}
