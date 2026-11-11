package uk.bit1.engagement.web.patient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.bit1.engagement.application.export.PatientExport;
import uk.bit1.engagement.application.patient.PatientQueryService;
import uk.bit1.engagement.application.patient.query.PatientDetail;
import uk.bit1.engagement.application.patient.query.PatientListQuery;
import uk.bit1.engagement.application.patient.query.PatientPage;
import uk.bit1.engagement.application.patient.query.TimelinePage;

@RestController
@RequestMapping("/api/patients")
class PatientApiController {

  private final PatientQueryService patients;
  private final PatientExport export;

  PatientApiController(PatientQueryService patients, PatientExport export) {
    this.patients = patients;
    this.export = export;
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

  @Operation(
      summary = "Download the filtered, sorted list as CSV (ADMIN only)",
      description =
          "Same search, filters and sort as the list, every matching row, no paging. Includes"
              + " identifiers. Times are London local times with their offset.")
  @GetMapping(value = "/export.csv", produces = "text/csv")
  void export(
      @Parameter(description = "As for the list") @RequestParam(required = false) String q,
      @Parameter(description = "As for the list") @RequestParam(required = false)
          List<String> status,
      @Parameter(description = "As for the list") @RequestParam(required = false)
          List<String> module,
      @Parameter(description = "As for the list") @RequestParam(required = false) String from,
      @Parameter(description = "As for the list") @RequestParam(required = false) String to,
      @Parameter(description = "As for the list") @RequestParam(required = false) String sort,
      @Parameter(description = "As for the list") @RequestParam(required = false) String dir,
      Principal user,
      HttpServletResponse response)
      throws IOException {
    PatientListQuery query =
        PatientListQuery.parse(q, status, module, from, to, sort, dir, null, null);
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"patients.csv\"");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    Writer out =
        new BufferedWriter(
            new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
    export.write(query, out, user.getName());
  }

  @Operation(
      summary = "One Patient: Status, age, identifiers, data-quality flags and Engagement summary")
  @GetMapping("/{uuid}")
  PatientDetail detail(@Parameter(description = "The Patient's UUID") @PathVariable String uuid) {
    return patients.detail(PatientRequest.parse(uuid, null).id());
  }

  @Operation(summary = "A Patient's Actions, newest first, 50 per page")
  @GetMapping("/{uuid}/actions")
  TimelinePage actions(
      @Parameter(description = "The Patient's UUID") @PathVariable String uuid,
      @Parameter(description = "The nextCursor of the previous page. Omit for the newest page.")
          @RequestParam(required = false)
          String cursor) {
    PatientRequest request = PatientRequest.parse(uuid, cursor);
    return patients.timeline(request.id(), request.cursor());
  }
}
