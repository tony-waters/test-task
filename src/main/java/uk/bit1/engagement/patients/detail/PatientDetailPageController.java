package uk.bit1.engagement.patients.detail;

import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import uk.bit1.engagement.patients.timeline.PatientRequest;
import uk.bit1.engagement.patients.timeline.Timeline;
import uk.bit1.engagement.shared.InvalidParametersException;
import uk.bit1.engagement.shared.NotFoundException;

/** The patient detail page: one Patient and a page of their timeline. */
@Controller
class PatientDetailPageController {

  private final PatientDetails patients;
  private final Timeline timeline;

  PatientDetailPageController(PatientDetails patients, Timeline timeline) {
    this.patients = patients;
    this.timeline = timeline;
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
      model.addAttribute("timeline", timeline.page(request.id(), request.cursor()));
    } catch (NotFoundException e) {
      response.setStatus(HttpServletResponse.SC_NOT_FOUND);
      model.addAttribute("message", e.getMessage());
      return "patients/missing";
    }
    model.addAttribute("firstPage", request.cursor() == null);
    return "patients/detail";
  }
}
