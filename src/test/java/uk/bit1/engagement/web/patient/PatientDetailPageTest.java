package uk.bit1.engagement.web.patient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

@IntegrationTest
class PatientDetailPageTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void theListLinksToEachPatient() throws Exception {
    mvc.perform(get("/patients").param("q", "leaver"))
        .andExpect(content().string(containsString("href=\"/patients/" + StandardData.DAN + "\"")));
  }

  @Test
  void showsTheEngagementSummaryAndMarksPostDischargeActionsInLondonTime() throws Exception {
    mvc.perform(get("/patients/{id}", StandardData.DAN))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Leaver")))
        .andExpect(content().string(containsString("Engagement summary")))
        .andExpect(content().string(containsString("FATIGUE")))
        .andExpect(content().string(containsString("Task completed")))
        // 09:00 UTC on 1 June is 10:00 in London (BST).
        .andExpect(content().string(containsString("1 Jun 2025, 10:00:00")))
        .andExpect(content().string(containsString("After discharge")))
        .andExpect(content().string(not(containsString("Suspect timestamp"))));
  }

  @Test
  void spellsOutUnknownsAndShowsFlags() throws Exception {
    mvc.perform(get("/patients/{id}", StandardData.BERT))
        .andExpect(content().string(containsString("Unknown")))
        .andExpect(content().string(containsString("Not recorded")))
        .andExpect(content().string(containsString("No Actions recorded.")));
    mvc.perform(get("/patients/{id}", StandardData.CORA))
        .andExpect(content().string(containsString("Skipped registration")));
    mvc.perform(get("/patients/{id}", StandardData.EVE))
        .andExpect(content().string(containsString("Suspect timestamps")))
        .andExpect(content().string(containsString("Suspect timestamp</span>")));
  }

  @Test
  void anUnknownPatientIsA404Page() throws Exception {
    mvc.perform(get("/patients/{id}", "00000000-0000-4000-8000-0000000000ff"))
        .andExpect(status().isNotFound())
        .andExpect(content().string(containsString("Patient not found")));
  }

  @Test
  void aMalformedUuidIsA400Page() throws Exception {
    mvc.perform(get("/patients/{id}", "not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("not a valid patient ID")));
  }

  @Test
  void aBadCursorShowsTheNewestPageWithTheError() throws Exception {
    mvc.perform(get("/patients/{id}", StandardData.DAN).param("cursor", "garbage"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("is not a cursor this API returned")))
        .andExpect(content().string(containsString("After discharge")));
  }

  @Test
  void anUnfamiliarActionTypeGetsItsOwnColumn() throws Exception {
    mvc.perform(get("/patients/{id}", StandardData.ADA))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<th class=\"num\">Paused</th>")))
        .andExpect(content().string(containsString("<th class=\"num\">Completed</th>")));
  }
}
