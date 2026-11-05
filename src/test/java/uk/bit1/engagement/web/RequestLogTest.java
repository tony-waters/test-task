package uk.bit1.engagement.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class RequestLogTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void writesOneJsonLinePerRequest(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/patients").header("X-Request-Id", "trace-123"))
        .andExpect(header().string("X-Request-Id", "trace-123"));

    String line = onlyRequestLine(output);
    assertThat((String) JsonPath.read(line, "$.requestId")).isEqualTo("trace-123");
    assertThat((String) JsonPath.read(line, "$.role")).isEqualTo("CLINICIAN");
    assertThat((String) JsonPath.read(line, "$.method")).isEqualTo("GET");
    assertThat((String) JsonPath.read(line, "$.endpoint")).isEqualTo("/api/patients");
    assertThat((Integer) JsonPath.read(line, "$.status")).isEqualTo(200);
    assertThat((Integer) JsonPath.read(line, "$.durationMs")).isNotNegative();
  }

  @Test
  void logsTheSearchTermOnlyAsItsLength(CapturedOutput output) throws Exception {
    mvc.perform(get("/patients").param("q", "999 000 0018"));

    String line = onlyRequestLine(output);
    assertThat((Integer) JsonPath.read(line, "$.searchLength")).isEqualTo(12);
    assertThat(output.getOut()).doesNotContain("999 000 0018", "9990000018", "Testwell");
  }

  @Test
  void logsTheRoutePatternWithThePatientUuidSeparately(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/patients/{uuid}", StandardData.ADA));

    String line = onlyRequestLine(output);
    assertThat((String) JsonPath.read(line, "$.endpoint")).isEqualTo("/api/patients/{uuid}");
    assertThat((String) JsonPath.read(line, "$.patient")).isEqualTo(StandardData.ADA.toString());
    assertThat(output.getOut()).doesNotContain("Testwell", "9990000018", "HOS000001", "1980-06-15");
  }

  @Test
  void logsRejectedRequestsToo(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/patients").with(anonymous()));
    String unauthorised = onlyRequestLine(output);
    assertThat((String) JsonPath.read(unauthorised, "$.role")).isEqualTo("anonymous");
    assertThat((Integer) JsonPath.read(unauthorised, "$.status")).isEqualTo(401);
  }

  @Test
  void logsTheRoleOfAForbiddenRequest(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/patients/export.csv"));
    String forbidden = onlyRequestLine(output);
    assertThat((String) JsonPath.read(forbidden, "$.role")).isEqualTo("CLINICIAN");
    assertThat((Integer) JsonPath.read(forbidden, "$.status")).isEqualTo(403);
    assertThat((String) JsonPath.read(forbidden, "$.endpoint")).isEqualTo("/api/patients/export.csv");
  }

  @Test
  void anUnmatchedPathKeepsOnlyUuidsAndPlainWords() {
    assertThat(RequestLogFilter.endpoint(request("/patients/9990000018"))).isEqualTo("/patients/{redacted}");
    assertThat(RequestLogFilter.endpoint(request("/api/patients/Testwell/x")))
        .isEqualTo("/api/patients/{redacted}/x");
    assertThat(RequestLogFilter.endpoint(request("/api/patients/" + StandardData.ADA)))
        .isEqualTo("/api/patients/" + StandardData.ADA);
    assertThat(RequestLogFilter.endpoint(request("/"))).isEqualTo("/");
  }

  @Test
  void ignoresAnUnsafeRequestId(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/patients").header("X-Request-Id", "Ada Testwell 9990000018"));
    String line = onlyRequestLine(output);
    assertThat((String) JsonPath.read(line, "$.requestId")).doesNotContain("Testwell");
  }

  private static MockHttpServletRequest request(String path) {
    return new MockHttpServletRequest("GET", path);
  }

  private static String onlyRequestLine(CapturedOutput output) {
    List<String> lines =
        output.getOut().lines().filter(l -> l.contains("\"message\":\"request\"")).toList();
    assertThat(lines).hasSize(1);
    return lines.getFirst();
  }
}
