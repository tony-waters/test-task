package uk.bit1.engagement.patients.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.patients.timeline.Timeline;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

@IntegrationTest
class PatientDetailApiTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;
  @Autowired PatientDetails details;
  @Autowired Timeline timeline;
  @Autowired EntityManagerFactory emf;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void showsWhoThePatientIsAndWhereTheyAre() throws Exception {
    mvc.perform(get("/api/patients/{id}", StandardData.ADA))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(StandardData.ADA.toString()))
        .andExpect(jsonPath("$.status").value("REGISTERED"))
        .andExpect(jsonPath("$.age").value(46))
        .andExpect(jsonPath("$.gender").value("FEMALE"))
        .andExpect(jsonPath("$.nhsNumber").value("9990000018"))
        .andExpect(jsonPath("$.hospitalId").value("HOS000001"))
        .andExpect(jsonPath("$.registeredAt").value("2025-03-05T09:00:00Z"))
        .andExpect(jsonPath("$.dataQualityFlags", is(empty())))
        .andExpect(jsonPath("$.entityId").doesNotExist());
  }

  @Test
  void unknownDemographicsAreNull() throws Exception {
    mvc.perform(get("/api/patients/{id}", StandardData.BERT))
        .andExpect(jsonPath("$.age", is(nullValue())))
        .andExpect(jsonPath("$.gender", is(nullValue())))
        .andExpect(jsonPath("$.nhsNumber", is(nullValue())))
        .andExpect(jsonPath("$.status").value("INVITED"))
        .andExpect(jsonPath("$.engagement.total").value(0))
        .andExpect(jsonPath("$.engagement.modules", hasSize(3)));
  }

  @Test
  void flagsSkippedRegistration() throws Exception {
    mvc.perform(get("/api/patients/{id}", StandardData.CORA))
        .andExpect(jsonPath("$.status").value("DISCHARGED"))
        .andExpect(jsonPath("$.dataQualityFlags", contains("SKIPPED_REGISTRATION")));
  }

  @Test
  void flagsSuspectTimestamps() throws Exception {
    mvc.perform(get("/api/patients/{id}", StandardData.EVE))
        .andExpect(jsonPath("$.dataQualityFlags", contains("SUSPECT_TIMESTAMPS")));
    mvc.perform(get("/api/patients/{id}/actions", StandardData.EVE))
        .andExpect(jsonPath("$.content[0].suspectTimestamp").value(true));
  }

  @Test
  void summarisesEngagementPerModuleAndTopic() throws Exception {
    mvc.perform(get("/api/patients/{id}", StandardData.DAN))
        .andExpect(jsonPath("$.engagement.total").value(4))
        .andExpect(jsonPath("$.engagement.firstActionAt").value("2025-04-01T09:00:00Z"))
        .andExpect(jsonPath("$.engagement.lastActionAt").value("2025-06-01T09:00:00Z"))
        .andExpect(jsonPath("$.engagement.modules[*].module", contains("ASSESSMENT", "DIARY", "PROGRAMME")))
        .andExpect(jsonPath("$.engagement.modules[2].total").value(4))
        .andExpect(jsonPath("$.engagement.modules[2].topics[0].topic").value("FATIGUE"))
        .andExpect(jsonPath("$.engagement.modules[2].topics[0].countsByActionType.STARTED").value(1))
        // The look-alike duplicates are two Actions, and both count.
        .andExpect(jsonPath("$.engagement.modules[2].topics[0].countsByActionType['TASK-COMPLETED']").value(2))
        // The post-discharge FINISHED counts like any other.
        .andExpect(jsonPath("$.engagement.modules[2].topics[0].countsByActionType.FINISHED").value(1))
        .andExpect(jsonPath("$.engagement.modules[0].total").value(0));
  }

  @Test
  void timelineIsNewestFirstAndMarksPostDischargeActions() throws Exception {
    mvc.perform(get("/api/patients/{id}/actions", StandardData.DAN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(50))
        .andExpect(jsonPath("$.nextCursor", is(nullValue())))
        .andExpect(jsonPath("$.content", hasSize(4)))
        .andExpect(jsonPath("$.content[*].recordedAt", contains(
            "2025-06-01T09:00:00Z", "2025-04-02T09:00:00Z", "2025-04-02T09:00:00Z", "2025-04-01T09:00:00Z")))
        .andExpect(jsonPath("$.content[*].postDischarge", contains(true, false, false, false)))
        .andExpect(jsonPath("$.content[*].suspectTimestamp", contains(false, false, false, false)))
        .andExpect(jsonPath("$.content[1:3].id", containsInAnyOrder(
            StandardData.DUPLICATE_A.toString(), StandardData.DUPLICATE_B.toString())));
  }

  @Test
  void anUnknownPatientIsA404ProblemDetail() throws Exception {
    UUID nobody = UUID.fromString("00000000-0000-4000-8000-0000000000ff");
    for (String path : new String[] {"/api/patients/{id}", "/api/patients/{id}/actions"}) {
      mvc.perform(get(path, nobody))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.title").value("Not found"));
    }
  }

  @Test
  void aMalformedUuidIsA400ProblemDetail() throws Exception {
    for (String bad : new String[] {"not-a-uuid", "1-1-1-1-1", "00000000-0000-4000-8000-00000000000g"}) {
      mvc.perform(get("/api/patients/{id}", bad))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.errors[*].parameter", contains("uuid")));
    }
  }

  @Test
  void aBadCursorIsA400ProblemDetail() throws Exception {
    mvc.perform(get("/api/patients/{id}/actions", StandardData.DAN).param("cursor", "garbage"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid request parameters"))
        .andExpect(jsonPath("$.errors[*].parameter", contains("cursor")));
  }

  @Test
  void reportsABadUuidAndABadCursorTogether() throws Exception {
    mvc.perform(get("/api/patients/{id}/actions", "nope").param("cursor", "garbage"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[*].parameter", contains("uuid", "cursor")));
  }

  @Test
  void detailAndTimelineCostAFixedNumberOfStatements() {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();

    stats.clear();
    details.detail(StandardData.DAN);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(3);

    stats.clear();
    timeline.page(StandardData.DAN, null);
    assertThat(stats.getPrepareStatementCount()).isEqualTo(3);
  }

  @Test
  void theOpenApiDescriptionCoversBothEndpoints() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(jsonPath("$.paths['/api/patients/{uuid}'].get").exists())
        .andExpect(jsonPath("$.paths['/api/patients/{uuid}/actions'].get").exists());
  }
}
