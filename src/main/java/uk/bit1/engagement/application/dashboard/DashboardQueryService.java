package uk.bit1.engagement.application.dashboard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.application.dashboard.query.Funnel;
import uk.bit1.engagement.application.dashboard.query.Journey;
import uk.bit1.engagement.application.dashboard.query.StatusCounts;
import uk.bit1.engagement.application.dashboard.query.WeeklyActive;
import uk.bit1.engagement.application.dashboard.query.WeeklyActiveQuery;
import uk.bit1.engagement.application.dashboard.query.WeeklyActiveQuery.WeekRange;
import uk.bit1.engagement.domain.common.London;

/**
 * What the dashboard page and the API share. Every figure covers the whole population: the
 * patient-list filters never apply here.
 */
@Service
@Transactional(readOnly = true)
public class DashboardQueryService {

  private final DashboardQueryRepository queries;

  DashboardQueryService(DashboardQueryRepository queries) {
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
        .resolve(queries.recordedSpan().londonDates())
        .map(range -> WeeklyActive.of(range, activeByWeek(range)))
        .orElseGet(WeeklyActive::empty);
  }

  private Map<LocalDate, Long> activeByWeek(WeekRange range) {
    return queries
        .activeByWeek(London.startOf(range.firstWeek()), London.startOf(range.lastWeek().plusWeeks(1)))
        .stream()
        .collect(
            Collectors.toMap(
                DashboardQueryRepository.WeekCount::getWeek,
                DashboardQueryRepository.WeekCount::getPatients));
  }

  public record Overview(StatusCounts status, Funnel funnel) {}
}
