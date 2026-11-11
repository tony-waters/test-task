package uk.bit1.engagement.patients.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import uk.bit1.engagement.patients.list.PatientSearchRepository;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.patients.list.PatientListQuery;
import uk.bit1.engagement.patients.list.PatientSummary;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
@ExtendWith(OutputCaptureExtension.class)
class PatientExportTest {

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;
  @Autowired PatientSearchRepository patients;
  @Autowired JdbcTemplate jdbc;
  @Autowired PlatformTransactionManager transactions;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void exportsEveryMatchingPatientAsCsvWithIdentifiers() throws Exception {
    String csv =
        mvc.perform(get("/api/patients/export.csv"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"patients.csv\""))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    List<String> lines = lines(csv);
    assertThat(lines.getFirst()).isEqualTo(String.join(",", PatientCsv.HEADER));
    assertThat(lines).hasSize(1 + StandardData.PATIENTS); // all pages, not the first 25
    assertThat(csv).contains("9990000018", "HOS000001", "1980-06-15", "Testwell");
  }

  @Test
  void usesTheListsFiltersAndSort() throws Exception {
    String params = "?status=REGISTERED&module=ASSESSMENT&sort=familyName&dir=desc";
    String json =
        mvc.perform(get("/api/patients" + params + "&size=100"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> listIds = JsonPath.read(json, "$.content[*].id");

    String csv =
        mvc.perform(get("/api/patients/export.csv" + params))
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> csvIds = lines(csv).stream().skip(1).map(l -> l.split(",")[0]).toList();

    assertThat(csvIds).isNotEmpty().isEqualTo(listIds);
  }

  @Test
  void aBadFilterIsA400ProblemDetail() throws Exception {
    mvc.perform(get("/api/patients/export.csv").param("status", "LOST"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].parameter").value("status"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void writesOneAuditLineWithoutIdentifiers(CapturedOutput output) throws Exception {
    mvc.perform(get("/api/patients/export.csv").param("q", "testwell").param("status", "REGISTERED"))
        .andExpect(status().isOk());

    List<String> audit =
        output.getOut().lines().filter(l -> l.contains("\"patient export\"")).toList();
    assertThat(audit).hasSize(1);
    String line = audit.getFirst();
    assertThat((String) JsonPath.read(line, "$.user")).isEqualTo("admin");
    assertThat((Integer) JsonPath.read(line, "$.rows")).isEqualTo(1);
    assertThat((Boolean) JsonPath.read(line, "$.complete")).isTrue();
    assertThat((Integer) JsonPath.read(line, "$.filters.searchLength")).isEqualTo(8);
    assertThat((List<String>) JsonPath.read(line, "$.filters.status")).containsExactly("REGISTERED");
    assertThat(line).doesNotContainIgnoringCase("testwell").doesNotContain("9990000018", "HOS000001");
  }

  /**
   * The demonstration that the export streams. While the first row is being handled, PostgreSQL
   * holds an open cursor for the query, so the rest of the result is still in the database rather
   * than in the JVM. Without a fetch size the driver reads every row up front and no cursor
   * remains.
   */
  @Test
  void readsThroughADatabaseCursor() {
    PatientListQuery all = PatientListQuery.defaults();
    TransactionTemplate readOnly = new TransactionTemplate(transactions);
    readOnly.setReadOnly(true);

    readOnly.executeWithoutResult(
        tx -> {
          try (Stream<PatientSummary> rows = patients.stream(all, 5)) {
            Iterator<PatientSummary> it = rows.iterator();
            it.next();
            assertThat(openCursors()).isEqualTo(1);
            int seen = 1;
            while (it.hasNext()) {
              it.next();
              seen++;
            }
            assertThat(seen).isEqualTo(StandardData.PATIENTS);
          }
        });

    readOnly.executeWithoutResult(
        tx -> {
          try (Stream<PatientSummary> rows = patients.stream(all, 0)) {
            rows.iterator().next();
            assertThat(openCursors()).isZero();
          }
        });
  }

  /** Named portals on this connection: the JDBC driver names one only when it reads in batches. */
  private int openCursors() {
    return jdbc.queryForObject(
        "select count(*) from pg_cursors where name <> '' and statement ilike '%from patient%'",
        Integer.class);
  }

  private static List<String> lines(String csv) {
    return Arrays.stream(csv.split("\r\n")).filter(l -> !l.isEmpty()).toList();
  }
}
