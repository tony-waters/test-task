package uk.bit1.engagement.patients.detail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.bit1.engagement.patients.timeline.PatientRequest;

@RestController
@RequestMapping("/api/patients")
@Tag(name = "patient-api-controller") // one OpenAPI group for every /api/patients controller
class PatientDetailApiController {

  private final PatientDetails patients;

  PatientDetailApiController(PatientDetails patients) {
    this.patients = patients;
  }

  @Operation(
      summary = "One Patient: Status, age, identifiers, data-quality flags and Engagement summary")
  @GetMapping("/{uuid}")
  PatientDetail detail(@Parameter(description = "The Patient's UUID") @PathVariable String uuid) {
    return patients.detail(PatientRequest.parse(uuid, null).id());
  }
}
