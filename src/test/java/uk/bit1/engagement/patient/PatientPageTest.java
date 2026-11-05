package uk.bit1.engagement.patient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

@IntegrationTest
class PatientPageTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void rootRedirectsToThePatientList() throws Exception {
    mvc.perform(get("/")).andExpect(redirectedUrl("/patients"));
  }

  @Test
  void rendersTheListWithUnknownsSpelledOut() throws Exception {
    mvc.perform(get("/patients").param("q", "sample"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Sample")))
        .andExpect(content().string(containsString("Unknown")))
        .andExpect(content().string(containsString("Not recorded")))
        .andExpect(content().string(not(containsString("Testwell"))));
  }

  @Test
  void showsInvitationTimesInLondon() throws Exception {
    mvc.perform(get("/patients").param("q", "midnight"))
        .andExpect(content().string(containsString("1 Jul 2025")));
  }

  @Test
  void keepsStateInLinks() throws Exception {
    mvc.perform(get("/patients").param("status", "REGISTERED").param("size", "10"))
        .andExpect(content().string(containsString("/patients?status=REGISTERED&amp;page=1&amp;size=10")))
        .andExpect(content().string(containsString("/patients?status=REGISTERED&amp;sort=familyName&amp;dir=asc&amp;size=10")));
  }

  @Test
  void encodesALiteralPlusInLinksSoTheSearchSurvives() throws Exception {
    mvc.perform(get("/patients").param("q", "a+b c"))
        .andExpect(content().string(containsString("/patients?q=a%2Bb%20c&amp;sort=familyName")));
  }

  @Test
  void explainsInvalidParameters() throws Exception {
    mvc.perform(get("/patients").param("size", "7"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("must be one of 10, 25, 50, 100")));
  }

  @Test
  void showsWhoIsSignedInWithASignOutForm() throws Exception {
    mvc.perform(get("/patients"))
        .andExpect(content().string(containsString("action=\"/logout\"")))
        .andExpect(content().string(containsString(">clinician<")))
        .andExpect(content().string(containsString("name=\"_csrf\"")));
  }

  @Test
  void onlyAdminsSeeTheExportLink() throws Exception {
    mvc.perform(get("/patients").param("status", "REGISTERED"))
        .andExpect(content().string(not(containsString("export.csv"))));
  }

  @Test
  @WithMockUser(username = "admin", roles = "ADMIN")
  void theExportLinkKeepsTheFiltersAndSortButNotThePage() throws Exception {
    mvc.perform(get("/patients").param("status", "REGISTERED").param("size", "10").param("page", "1"))
        .andExpect(content().string(containsString("href=\"/api/patients/export.csv?status=REGISTERED\"")));
  }
}
