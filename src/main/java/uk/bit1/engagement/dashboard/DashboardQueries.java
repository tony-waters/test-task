package uk.bit1.engagement.dashboard;

import jakarta.persistence.EntityManager;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import uk.bit1.engagement.dashboard.WeeklyActiveQuery.DateSpan;
import uk.bit1.engagement.dashboard.WeeklyActiveQuery.WeekRange;
import uk.bit1.engagement.shared.London;

/**
 * The dashboard's reads, each one statement over the whole population.
 *
 * <p>Patient-level figures fetch every Patient's three journey timestamps and aggregate in Java,
 * where the rules are unit-tested: Patients are few, even at 100× volume. Weekly-active counts
 * group in the database, since Actions are the large table.
 */
@Component
class DashboardQueries {

  private final EntityManager em;

  DashboardQueries(EntityManager em) {
    this.em = em;
  }

  List<Journey> journeys() {
    return em.createQuery(
            "select new uk.bit1.engagement.dashboard.Journey(p.invitedAt, p.registeredAt,"
                + " p.dischargedAt) from Patient p",
            Journey.class)
        .getResultList();
  }

  /** The London dates of the first and last Action, or empty when there are none. */
  Optional<DateSpan> actionSpan() {
    Object[] row =
        (Object[])
            em.createNativeQuery("select min(recorded_at), max(recorded_at) from action")
                .getSingleResult();
    if (row[0] == null) {
      return Optional.empty();
    }
    return Optional.of(
        new DateSpan(London.dateOf(toInstant(row[0])), London.dateOf(toInstant(row[1]))));
  }

  /**
   * Distinct Patients with an Action in each London ISO week of the range, keyed by Monday. Weeks
   * with none are absent. The bounds are London midnights, so the filter and the grouping agree
   * across the BST change.
   */
  Map<LocalDate, Long> activeByWeek(WeekRange range) {
    @SuppressWarnings("unchecked")
    List<Object[]> rows =
        em.createNativeQuery(
                """
                select cast(date_trunc('week', recorded_at at time zone 'Europe/London') as date),
                       count(distinct patient_id)
                  from action
                 where recorded_at >= :start and recorded_at < :end
                 group by 1
                """)
            .setParameter("start", London.startOf(range.firstWeek()))
            .setParameter("end", London.startOf(range.lastWeek().plusWeeks(1)))
            .getResultList();
    Map<LocalDate, Long> counts = new LinkedHashMap<>();
    for (Object[] row : rows) {
      counts.put(toLocalDate(row[0]), ((Number) row[1]).longValue());
    }
    return counts;
  }

  private static Instant toInstant(Object value) {
    return switch (value) {
      case Instant i -> i;
      case OffsetDateTime o -> o.toInstant();
      case Timestamp t -> t.toInstant();
      default -> throw new IllegalStateException("Unexpected timestamp type " + value.getClass());
    };
  }

  private static LocalDate toLocalDate(Object value) {
    return switch (value) {
      case LocalDate d -> d;
      case Date d -> d.toLocalDate();
      default -> throw new IllegalStateException("Unexpected date type " + value.getClass());
    };
  }
}
