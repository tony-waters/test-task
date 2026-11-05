package uk.bit1.engagement.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.TestDump;
import uk.bit1.engagement.support.TestDump.PatientRow;

/**
 * Walks whole timelines through the API. Runs on its own data, so it empties the shared database
 * before and after.
 */
@IntegrationTest
class TimelinePagingTest {

  /** Not a multiple of 50, so the last page is partial. */
  private static final int ACTIONS = 137;

  /** Actions share a timestamp in runs of 7, so ties straddle the page boundaries at 50 and 100. */
  private static final int SAME_TIME_RUN = 7;

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;
  @Autowired JdbcTemplate jdbc;
  @TempDir Path dir;

  private UUID busy;
  private UUID quiet;

  @BeforeEach
  void load() {
    emptyTheDatabase();
    TestDump dump = new TestDump();
    PatientRow busyRow = dump.patient("Busy", "Recorder", "2025-01-01 10:00:00");
    busyRow.registered = "2025-01-02 10:00:00";
    PatientRow quietRow = dump.patient("Quiet", "Neighbour", "2025-01-01 10:00:00");
    quietRow.registered = "2025-01-02 10:00:00";
    for (int i = 0; i < ACTIONS; i++) {
      String time = "2025-03-%02d 09:00:00".formatted(1 + i / SAME_TIME_RUN);
      dump.action(busyRow, time, "PROGRAMME", "FATIGUE", "TASK-COMPLETED");
      if (i % 10 == 0) {
        // Interleaved with the busy Patient's, at the same times: must never leak in.
        dump.action(quietRow, time, "DIARY", "BLADDER", "STARTED");
      }
    }
    busy = busyRow.uuid;
    quiet = quietRow.uuid;
    loader.load(dump.write(dir));
  }

  @AfterEach
  void emptyTheDatabase() {
    jdbc.execute("truncate action, patient restart identity");
  }

  @Test
  void pagesHaveNoGapsOrRepeatsAcrossTiedTimestamps() throws Exception {
    List<List<String>> pages = walk(busy);

    assertThat(pages).extracting(List::size).containsExactly(50, 50, 37);
    List<String> walked = pages.stream().flatMap(List::stream).toList();
    assertThat(walked).doesNotHaveDuplicates().hasSize(ACTIONS).containsExactlyElementsOf(expectedOrder(busy));
  }

  @Test
  void anotherPatientsActionsNeverAppear() throws Exception {
    List<String> walked = walk(quiet).stream().flatMap(List::stream).toList();

    assertThat(walked).containsExactlyElementsOf(expectedOrder(quiet));
  }

  @Test
  void anActionLoadedBetweenPagesDoesNotShiftLaterPages() throws Exception {
    String first = mvc.perform(get("/api/patients/{id}/actions", busy)).andReturn().getResponse().getContentAsString();
    String cursor = JsonPath.read(first, "$.nextCursor");
    List<String> expectedSecond = expectedOrder(busy).subList(50, 100);

    jdbc.update(
        "insert into action (uuid, patient_id, recorded_at, module, topic, action_type, source_created_at)"
            + " select ?, id, timestamptz '2025-12-01 09:00:00+00', 'DIARY', 'BLADDER', 'STARTED', now()"
            + " from patient where uuid = ?",
        UUID.randomUUID(), busy);

    String second =
        mvc.perform(get("/api/patients/{id}/actions", busy).param("cursor", cursor))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> secondIds = JsonPath.read(second, "$.content[*].id");
    assertThat(secondIds).containsExactlyElementsOf(expectedSecond);
  }

  /** Follows nextCursor from the newest page to the last, returning each page's Action UUIDs. */
  private List<List<String>> walk(UUID patient) throws Exception {
    List<List<String>> pages = new ArrayList<>();
    String cursor = null;
    do {
      MockHttpServletRequestBuilder request = get("/api/patients/{id}/actions", patient);
      if (cursor != null) {
        request.param("cursor", cursor);
      }
      String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      pages.add(JsonPath.read(body, "$.content[*].id"));
      cursor = JsonPath.read(body, "$.nextCursor");
      assertThat(pages).as("pages walked").hasSizeLessThan(10);
    } while (cursor != null);
    return pages;
  }

  private List<String> expectedOrder(UUID patient) {
    return jdbc.queryForList(
        "select a.uuid::text from action a join patient p on p.id = a.patient_id"
            + " where p.uuid = ? order by a.recorded_at desc, a.id desc",
        String.class,
        patient);
  }
}
