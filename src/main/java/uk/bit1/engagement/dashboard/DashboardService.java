package uk.bit1.engagement.dashboard;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the dashboard page and the API share. Every figure covers the whole population: the
 * patient-list filters never apply here.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

  private final DashboardQueries queries;

  DashboardService(DashboardQueries queries) {
    this.queries = queries;
  }

  /** Current-status buckets. One statement. */
  public StatusCounts status() {
    return StatusCounts.from(queries.journeys());
  }

  /** The strict-progression funnel with median transition times. One statement. */
  public Funnel funnel() {
    return Funnel.from(queries.journeys());
  }

  /** Status buckets and funnel together, from one read. One statement. */
  public Overview overview() {
    List<Journey> journeys = queries.journeys();
    return new Overview(StatusCounts.from(journeys), Funnel.from(journeys));
  }

  /** Weekly active Patients. Two statements: the Action date span, then the counts. */
  public WeeklyActive weeklyActive(WeeklyActiveQuery query) {
    return query
        .resolve(queries.actionSpan())
        .map(range -> WeeklyActive.of(range, queries.activeByWeek(range)))
        .orElseGet(WeeklyActive::empty);
  }

  public record Overview(StatusCounts status, Funnel funnel) {}
}
