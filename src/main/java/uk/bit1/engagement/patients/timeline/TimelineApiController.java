package uk.bit1.engagement.patients.timeline;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "patient-api-controller") // one OpenAPI group for every /api/patients controller
class TimelineApiController {

  private final Timeline timeline;

  TimelineApiController(Timeline timeline) {
    this.timeline = timeline;
  }

  @Operation(summary = "A Patient's Actions, newest first, 50 per page")
  @GetMapping("/{uuid}/actions")
  TimelinePage actions(
      @Parameter(description = "The Patient's UUID") @PathVariable String uuid,
      @Parameter(description = "The nextCursor of the previous page. Omit for the newest page.")
          @RequestParam(required = false)
          String cursor) {
    PatientRequest request = PatientRequest.parse(uuid, cursor);
    return timeline.page(request.id(), request.cursor());
  }
}
