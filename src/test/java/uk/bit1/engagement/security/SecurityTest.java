package uk.bit1.engagement.security;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

/** Real credentials (the local defaults) through the real filter chains. */
@IntegrationTest
class SecurityTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void theApiWithoutCredentialsIsA401ProblemDetail() throws Exception {
    mvc.perform(get("/api/patients").with(anonymous()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", startsWith("Basic")))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.title").value("Unauthorized"))
        .andExpect(jsonPath("$.instance").value("/api/patients"));
  }

  @Test
  void aWrongPasswordIsA401() throws Exception {
    mvc.perform(get("/api/patients").with(anonymous()).with(httpBasic("clinician", "wrong")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.status").value(401));
  }

  @Test
  void bothUsersCanReadTheApi() throws Exception {
    mvc.perform(get("/api/patients").with(httpBasic("clinician", "clinician")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/dashboard/status").with(httpBasic("admin", "admin")))
        .andExpect(status().isOk());
  }

  @Test
  void clinicianCannotExport() throws Exception {
    mvc.perform(get("/api/patients/export.csv").with(httpBasic("clinician", "clinician")))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.title").value("Forbidden"));
  }

  @Test
  void adminCanExport() throws Exception {
    mvc.perform(get("/api/patients/export.csv").with(httpBasic("admin", "admin")))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/csv"));
  }

  @Test
  void pagesRedirectToTheLoginForm() throws Exception {
    mvc.perform(get("/patients").with(anonymous()))
        .andExpect(status().isFound())
        .andExpect(redirectedUrl("/login"));
    mvc.perform(get("/login").with(anonymous())).andExpect(status().isOk());
  }

  @Test
  void theLoginFormSignsIn() throws Exception {
    mvc.perform(formLogin().user("clinician").password("clinician"))
        .andExpect(redirectedUrl("/patients"));
    mvc.perform(formLogin().user("clinician").password("wrong"))
        .andExpect(redirectedUrl("/login?error"));
  }

  @Test
  void healthProbesArePublicAndReadinessIncludesTheDatabase() throws Exception {
    mvc.perform(get("/actuator/health").with(anonymous()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"))
        .andExpect(jsonPath("$.components").doesNotExist());
    mvc.perform(get("/actuator/health/liveness").with(anonymous()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/actuator/health/readiness").with(anonymous()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mvc.perform(get("/actuator/health/readiness").with(httpBasic("admin", "admin")))
        .andExpect(jsonPath("$.components.db.status").value("UP"))
        .andExpect(jsonPath("$.components.readinessState.status").value("UP"));
  }

  @Test
  void onlyHealthIsPublic() throws Exception {
    mvc.perform(get("/actuator/metrics").with(anonymous())).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/info").with(anonymous())).andExpect(status().isUnauthorized());
    mvc.perform(get("/actuator/metrics").with(httpBasic("clinician", "clinician")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/actuator/metrics").with(httpBasic("admin", "admin")))
        .andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs").with(anonymous())).andExpect(status().isFound());
  }
}
