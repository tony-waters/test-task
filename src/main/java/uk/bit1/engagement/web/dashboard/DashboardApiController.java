package uk.bit1.engagement.web.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.bit1.engagement.application.dashboard.DashboardQueryService;
import uk.bit1.engagement.application.dashboard.query.Funnel;
import uk.bit1.engagement.application.dashboard.query.StatusCounts;
import uk.bit1.engagement.application.dashboard.query.WeeklyActive;
import uk.bit1.engagement.application.dashboard.query.WeeklyActiveQuery;

/** Whole-population aggregates. The patient-list filters do not apply. */
@RestController
@RequestMapping("/api/dashboard")
class DashboardApiController {

  private final DashboardQueryService dashboard;

  DashboardApiController(DashboardQueryService dashboard) {
    this.dashboard = dashboard;
  }

  @Operation(summary = "Patients by current Status. The buckets are mutually exclusive.")
  @GetMapping("/status")
  StatusCounts status() {
    return dashboard.status();
  }

  @Operation(
      summary =
          "Strict-progression funnel with conversion rates and median transition times."
              + " Skipped registrations are left out of Discharged and counted separately.")
  @GetMapping("/funnel")
  Funnel funnel() {
    return dashboard.funnel();
  }

  @Operation(summary = "Patients with at least one Action in each London ISO week")
  @GetMapping("/weekly-active")
  WeeklyActive weeklyActive(
      @Parameter(description = "A London date in the first week (yyyy-mm-dd). Default: the week of the first Action.")
          @RequestParam(required = false)
          String from,
      @Parameter(description = "A London date in the last week (yyyy-mm-dd). Default: the week of the last Action.")
          @RequestParam(required = false)
          String to) {
    return dashboard.weeklyActive(WeeklyActiveQuery.parse(from, to));
  }
}
