package uk.bit1.engagement.web.dashboard;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uk.bit1.engagement.application.common.InvalidParametersException;
import uk.bit1.engagement.application.dashboard.DashboardQueryService;
import uk.bit1.engagement.application.dashboard.query.WeeklyActive;
import uk.bit1.engagement.application.dashboard.query.WeeklyActiveQuery;

/** The dashboard page. The weekly-active range is the only state, and it is in the URL. */
@Controller
class DashboardPageController {

  private final DashboardQueryService dashboard;

  DashboardPageController(DashboardQueryService dashboard) {
    this.dashboard = dashboard;
  }

  /** A bad range shows the default range with the errors, as the patient list does. */
  @GetMapping("/dashboard")
  String dashboard(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      Model model,
      HttpServletResponse response) {
    WeeklyActiveQuery query;
    WeeklyActive weekly;
    try {
      query = WeeklyActiveQuery.parse(from, to);
      weekly = dashboard.weeklyActive(query);
    } catch (InvalidParametersException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      model.addAttribute("errors", e.getErrors());
      query = WeeklyActiveQuery.defaults();
      weekly = dashboard.weeklyActive(query);
    }
    model.addAttribute("overview", dashboard.overview());
    model.addAttribute("query", query);
    model.addAttribute("weekly", weekly);
    return "dashboard";
  }
}
