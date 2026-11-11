package uk.bit1.engagement.patients.timeline;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uk.bit1.engagement.core.Action;

/** The timeline's queries: keyset pages of one Patient's Actions. */
interface TimelineActions extends Repository<Action, Long> {

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
