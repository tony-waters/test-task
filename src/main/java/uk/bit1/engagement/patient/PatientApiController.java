package uk.bit1.engagement.patient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
class PatientApiController {

  private final PatientListService patients;

  PatientApiController(PatientListService patients) {
    this.patients = patients;
  }

  @Operation(summary = "Search, filter, sort and page the Patients")
  @GetMapping
  PatientPage list(
      @Parameter(description = "Case-insensitive substring of a name or hospital ID, or an NHS number (spaces ignored)")
          @RequestParam(required = false)
          String q,
      @Parameter(description = "Current Status: INVITED, REGISTERED or DISCHARGED. Repeat for more than one.")
          @RequestParam(required = false)
          List<String> status,
      @Parameter(description = "Module with at least one Action: ASSESSMENT, DIARY or PROGRAMME. Repeat for more than one.")
          @RequestParam(required = false)
          List<String> module,
      @Parameter(description = "First Invitation date to include, London time (yyyy-mm-dd)")
          @RequestParam(required = false)
          String from,
      @Parameter(description = "Last Invitation date to include, London time (yyyy-mm-dd)")
          @RequestParam(required = false)
          String to,
      @Parameter(description = "familyName, givenName, invitedAt, status, age or lastActionAt. Default invitedAt.")
          @RequestParam(required = false)
          String sort,
      @Parameter(description = "asc or desc. Default desc for the default sort, else asc.")
          @RequestParam(required = false)
          String dir,
      @Parameter(description = "Zero-based page number. Default 0.") @RequestParam(required = false)
          String page,
      @Parameter(description = "10, 25, 50 or 100. Default 25.") @RequestParam(required = false)
          String size) {
    return patients.find(PatientListQuery.parse(q, status, module, from, to, sort, dir, page, size));
  }
}
