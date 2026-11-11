package uk.bit1.engagement.application.dashboard;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import uk.bit1.engagement.application.dashboard.query.Journey;
import uk.bit1.engagement.application.dashboard.query.RecordedSpan;
import uk.bit1.engagement.domain.patient.Patient;

/**
 * The dashboard's reads, each one statement over the whole population.
 *
 * <p>Patient-level figures fetch every Patient's three journey timestamps and aggregate in Java,
 * where the rules are unit-tested: Patients are few, even at 100× volume. Weekly-active counts
 * group in the database, since Actions are the large table.
 */
interface DashboardQueryRepository extends Repository<Patient, Long> {

  @Query(
      """
      select new uk.bit1.engagement.application.dashboard.query.Journey(
                 p.invitedAt, p.registeredAt, p.dischargedAt)
        from Patient p
      """)
  List<Journey> journeys();

  /** The first and last recorded Action times. Always one row; both null when there are none. */
  @Query(
      """
      select new uk.bit1.engagement.application.dashboard.query.RecordedSpan(
                 min(a.recordedAt), max(a.recordedAt))
        from Action a
      """)
  RecordedSpan recordedSpan();

  /**
   * Distinct Patients with an Action in each London ISO week of {@code [start, end)}, keyed by
   * Monday. Weeks with none are absent. Pass London midnights, so the filter and the grouping agree
   * across the BST change.
   */
  @Query(
      nativeQuery = true,
      value =
          """
          select cast(date_trunc('week', recorded_at at time zone 'Europe/London') as date) as week,
                 count(distinct patient_id) as patients
            from action
           where recorded_at >= :start and recorded_at < :end
           group by 1
          """)
  List<WeekCount> activeByWeek(Instant start, Instant end);

  /** One row of {@link #activeByWeek}. */
  interface WeekCount {
    LocalDate getWeek();

    long getPatients();
  }
}
