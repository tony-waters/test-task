package uk.bit1.engagement.web.patient;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

@IntegrationTest
class PatientApiTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void defaultsToNewestInvitationFirstInPagesOf25() throws Exception {
    mvc.perform(get("/api/patients"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.totalElements").value(StandardData.PATIENTS))
        .andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.size").value(25))
        .andExpect(jsonPath("$.sort").value("invitedAt"))
        .andExpect(jsonPath("$.direction").value("desc"))
        .andExpect(jsonPath("$.content", hasSize(25)))
        .andExpect(jsonPath("$.content[0:5].familyName", contains("Midnight", "Sample", "Testwell", "Skipped", "Leaver")));
  }

  @Test
  void exposesUuidsAndDerivedFieldsOnly() throws Exception {
    mvc.perform(get("/api/patients").param("q", "testwell"))
        .andExpect(jsonPath("$.content[0].id").value(StandardData.ADA.toString()))
        .andExpect(jsonPath("$.content[0].age").value(46))
        .andExpect(jsonPath("$.content[0].status").value("REGISTERED"))
        .andExpect(jsonPath("$.content[0].invitedAt").value("2025-03-01T10:00:00Z"))
        .andExpect(jsonPath("$.content[0].lastActionAt").value("2025-04-01T08:00:00Z"))
        .andExpect(jsonPath("$.content[0].entityId").doesNotExist());
  }

  @Test
  void pagesThroughTheList() throws Exception {
    mvc.perform(get("/api/patients").param("size", "10").param("page", "2"))
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.content", hasSize(10)));
    mvc.perform(get("/api/patients").param("size", "10").param("page", "3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void aPageNumberWhoseOffsetOverflowsAnIntGivesAnEmptyPage() throws Exception {
    mvc.perform(get("/api/patients").param("page", "2147483647").param("size", "25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2147483647))
        .andExpect(jsonPath("$.totalElements").value(StandardData.PATIENTS))
        .andExpect(jsonPath("$.content", hasSize(0)));
    // 171798692 * 25 wraps to 4 as an int; it must not return real rows.
    mvc.perform(get("/api/patients").param("page", "171798692").param("size", "25"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content", hasSize(0)));
  }

  @Test
  void sortsByAgeWithUnknownAgesLastInBothDirections() throws Exception {
    mvc.perform(get("/api/patients").param("sort", "age").param("dir", "asc").param("size", "100"))
        .andExpect(jsonPath("$.content[0:3].familyName", contains("Midnight", "Skipped", "Testwell")))
        .andExpect(jsonPath("$.content[29].familyName").value("Sample"));
    mvc.perform(get("/api/patients").param("sort", "age").param("dir", "desc").param("size", "100"))
        .andExpect(jsonPath("$.content[0].familyName").value("Person01"))
        .andExpect(jsonPath("$.content[29].familyName").value("Sample"));
  }

  @Test
  void sortsByLastActionWithPatientsWithoutActionsLast() throws Exception {
    mvc.perform(get("/api/patients").param("sort", "lastActionAt").param("dir", "desc").param("size", "100"))
        .andExpect(jsonPath("$.content[0].familyName").value("Midnight"))
        .andExpect(jsonPath("$.content[28:30].familyName", containsInAnyOrder("Sample", "Skipped")));
    mvc.perform(get("/api/patients").param("sort", "lastActionAt").param("dir", "asc").param("size", "100"))
        .andExpect(jsonPath("$.content[28:30].familyName", containsInAnyOrder("Sample", "Skipped")));
  }

  @Test
  void sortsByStatusInJourneyOrder() throws Exception {
    mvc.perform(get("/api/patients").param("sort", "status").param("size", "100"))
        .andExpect(jsonPath("$.content[0].status").value("INVITED"))
        .andExpect(jsonPath("$.content[28:30].status", contains("DISCHARGED", "DISCHARGED")));
  }

  @Test
  void sortsByNames() throws Exception {
    mvc.perform(get("/api/patients").param("sort", "familyName").param("size", "100"))
        .andExpect(jsonPath("$.content[0].familyName").value("Leaver"))
        .andExpect(jsonPath("$.content[29].familyName").value("Testwell"));
    mvc.perform(get("/api/patients").param("sort", "givenName").param("dir", "desc"))
        .andExpect(jsonPath("$.content[0].givenName").value("Filler"));
  }

  @Test
  void searchesNamesCaseInsensitivelyBySubstring() throws Exception {
    mvc.perform(get("/api/patients").param("q", "ESTWE"))
        .andExpect(jsonPath("$.content[*].id", contains(StandardData.ADA.toString())));
  }

  @Test
  void searchMatchesWhateverTheServerLocale() throws Exception {
    Locale original = Locale.getDefault();
    Locale.setDefault(Locale.forLanguageTag("tr"));
    try {
      // Under Turkish rules "I" lower-cases to a dotless i, which would miss "midnight".
      mvc.perform(get("/api/patients").param("q", "MIDNIGHT"))
          .andExpect(jsonPath("$.content[*].id", contains(StandardData.EVE.toString())));
    } finally {
      Locale.setDefault(original);
    }
  }

  @Test
  void searchesNhsNumberIgnoringSpaces() throws Exception {
    mvc.perform(get("/api/patients").param("q", "999 000 0018"))
        .andExpect(jsonPath("$.content[*].id", contains(StandardData.ADA.toString())));
  }

  @Test
  void searchesHospitalIdCaseInsensitivelyBySubstring() throws Exception {
    mvc.perform(get("/api/patients").param("q", "hos000002"))
        .andExpect(jsonPath("$.content[*].id", contains(StandardData.BERT.toString())));
  }

  @Test
  void treatsLikeWildcardsLiterally() throws Exception {
    mvc.perform(get("/api/patients").param("q", "%"))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void filtersByOneOrMoreStatuses() throws Exception {
    mvc.perform(get("/api/patients").param("status", "DISCHARGED"))
        .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(StandardData.CORA.toString(), StandardData.DAN.toString())));
    mvc.perform(get("/api/patients").param("status", "INVITED").param("status", "DISCHARGED"))
        .andExpect(jsonPath("$.totalElements").value(3));
  }

  @Test
  void filtersByModuleOfAnyAction() throws Exception {
    mvc.perform(get("/api/patients").param("module", "DIARY"))
        .andExpect(jsonPath("$.content[*].id", contains(StandardData.ADA.toString())));
    mvc.perform(get("/api/patients").param("module", "DIARY").param("module", "PROGRAMME"))
        .andExpect(jsonPath("$.totalElements").value(2));
  }

  @Test
  void filtersByInvitationDateInLondonInclusively() throws Exception {
    // Eve was invited at 23:30 UTC on 30 June: 1 July in London.
    mvc.perform(get("/api/patients").param("from", "2025-07-01").param("to", "2025-07-01"))
        .andExpect(jsonPath("$.content[*].id", contains(StandardData.EVE.toString())));
    mvc.perform(get("/api/patients").param("from", "2025-06-30").param("to", "2025-06-30"))
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/patients").param("from", "2025-01-10").param("to", "2025-03-01"))
        .andExpect(jsonPath("$.content[*].familyName", containsInAnyOrder("Testwell", "Skipped", "Leaver")));
  }

  @Test
  void combinesDifferentFiltersWithAnd() throws Exception {
    mvc.perform(get("/api/patients").param("status", "DISCHARGED").param("module", "PROGRAMME"))
        .andExpect(jsonPath("$.content[*].id", contains(StandardData.DAN.toString())));
    mvc.perform(get("/api/patients").param("q", "filler").param("from", "2025-01-01"))
        .andExpect(jsonPath("$.totalElements").value(0));
  }

  @Test
  void returnsUnknownAgeAndGenderAsNull() throws Exception {
    mvc.perform(get("/api/patients").param("q", "sample"))
        .andExpect(jsonPath("$.content[0].age", is(nullValue())))
        .andExpect(jsonPath("$.content[0].gender", is(nullValue())))
        .andExpect(jsonPath("$.content[0].lastActionAt", is(nullValue())));
  }

  @Test
  void rejectsInvalidParametersAsProblemDetail() throws Exception {
    mvc.perform(get("/api/patients").param("size", "20").param("sort", "nhsNumber").param("status", "ACTIVE"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Invalid request parameters"))
        .andExpect(jsonPath("$.errors[*].parameter", containsInAnyOrder("size", "sort", "status")))
        .andExpect(jsonPath("$.errors[*].message", everyItem(not(is("")))));
  }

  @Test
  void servesTheOpenApiDescription() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists())
        .andExpect(jsonPath("$.paths['/api/patients'].get.parameters[*].name", hasItem("module")));
  }
}
