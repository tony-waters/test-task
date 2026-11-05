package uk.bit1.engagement.patient;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import uk.bit1.engagement.action.Module;
import uk.bit1.engagement.web.InvalidParametersException;
import uk.bit1.engagement.web.NotFoundException;

/** The patient list and detail pages. All filter, sort and page state is in the URL. */
@Controller
class PatientPageController {

  private final PatientService patients;

  PatientPageController(PatientService patients) {
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
    model.addAttribute("view", new PatientListView(query, patients.find(query)));
    model.addAttribute("statuses", Status.values());
    model.addAttribute("modules", Module.values());
    model.addAttribute("pageSizes", PatientListQuery.PAGE_SIZES);
    return "patients/list";
  }

  /**
   * One Patient and a page of their timeline. A bad cursor shows the newest page with the error,
   * as the list does; a bad or unknown UUID shows an error page.
   */
  @GetMapping("/patients/{uuid}")
  String detail(
      @PathVariable String uuid,
      @RequestParam(required = false) String cursor,
      Model model,
      HttpServletResponse response) {
    PatientRequest request;
    try {
      request = PatientRequest.parse(uuid, cursor);
    } catch (InvalidParametersException e) {
      response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      model.addAttribute("errors", e.getErrors());
      try {
        request = PatientRequest.parse(uuid, null);
      } catch (InvalidParametersException badUuid) {
        model.addAttribute("message", "That is not a valid patient ID.");
        return "patients/missing";
      }
    }
    try {
      PatientDetail patient = patients.detail(request.id());
      model.addAttribute("patient", patient);
      model.addAttribute(
          "flags", patient.dataQualityFlags().stream().map(Enum::name).collect(Collectors.toSet()));
      model.addAttribute("actionTypes", patient.engagement().columnActionTypes());
      model.addAttribute("timeline", patients.timeline(request.id(), request.cursor()));
    } catch (NotFoundException e) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      model.addAttribute("message", e.getMessage());
      return "patients/missing";
    }
    model.addAttribute("firstPage", request.cursor() == null);
    return "patients/detail";
  }
}
