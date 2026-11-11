package uk.bit1.engagement.patients.list;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uk.bit1.engagement.core.Module;
import uk.bit1.engagement.core.Status;
import uk.bit1.engagement.shared.InvalidParametersException;

/** The patient list page. All filter, sort and page state is in the URL. */
@Controller
class PatientListPageController {

  private final PatientSearchRepository patients;

  PatientListPageController(PatientSearchRepository patients) {
    this.patients = patients;
  }

  @GetMapping("/patients")
  String list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) List<String> module,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(required = false) String sort,
      @RequestParam(required = false) String dir,
      @RequestParam(required = false) String page,
      @RequestParam(required = false) String size,
      Model model,
      HttpServletResponse response) {
    PatientListQuery query;
    try {
      query = PatientListQuery.parse(q, status, module, from, to, sort, dir, page, size);
    } catch (InvalidParametersException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      model.addAttribute("errors", e.getErrors());
      query = PatientListQuery.defaults();
    }
    model.addAttribute("view", new PatientListView(query, patients.search(query)));
    model.addAttribute("statuses", Status.values());
    model.addAttribute("modules", Module.values());
    model.addAttribute("pageSizes", PatientListQuery.PAGE_SIZES);
    return "patients/list";
  }
}
