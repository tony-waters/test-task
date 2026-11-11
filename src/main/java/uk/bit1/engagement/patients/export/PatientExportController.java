package uk.bit1.engagement.patients.export;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.bit1.engagement.patients.list.PatientListQuery;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "patient-api-controller") // one OpenAPI group for every /api/patients controller
class PatientExportController {

  private final PatientExport export;

  PatientExportController(PatientExport export) {
    this.export = export;
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
}
