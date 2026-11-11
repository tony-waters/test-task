package uk.bit1.engagement.web.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.TestDump.PatientRow;
import uk.bit1.engagement.support.TestDump;

/**
 * The dashboard on a synthetic population with the supplied data's shape: 100 Patients, of whom 3
 * are only invited, 80 registered, 16 registered and discharged, and 1 discharged without
 * registering. Runs on its own data, so it empties the shared database before and after.
 */
@IntegrationTest
class DashboardApiTest {

  private static final String INVITED = "2025-01-06 10:00:00";
  private static final String REGISTERED = "2025-01-08 10:00:00"; // 2 days later
  private static final String DISCHARGED = "2025-04-01 10:00:00"; // 83 days after that

  @Autowired MockMvc mvc;
  @Autowired DumpLoader loader;
  @Autowired JdbcTemplate jdbc;
  @TempDir Path dir;

  @BeforeEach
  void load() {
    emptyTheDatabase();
    TestDump dump = new TestDump();
    PatientRow[] registered = new PatientRow[80];
    PatientRow[] discharged = new PatientRow[16];
    for (int i = 0; i < 3; i++) {
      dump.patient("Invited", "Person%03d".formatted(i), INVITED);
    }
    for (int i = 0; i < registered.length; i++) {
      registered[i] = dump.patient("Registered", "Person%03d".formatted(i), INVITED);
      registered[i].registered = REGISTERED;
    }
    for (int i = 0; i < discharged.length; i++) {
      discharged[i] = dump.patient("Discharged", "Person%03d".formatted(i), INVITED);
      discharged[i].registered = REGISTERED;
      discharged[i].discharged = DISCHARGED;
    }
    PatientRow skipped = dump.patient("Skipped", "Registration", INVITED);
    skipped.discharged = DISCHARGED;

    // Week of Mon 3 Mar 2025: two Patients, one of them twice.
    dump.action(registered[0], "2025-03-03 09:00:00", "DIARY", "BLADDER", "STARTED");
    dump.action(registered[0], "2025-03-04 09:00:00", "DIARY", "BLADDER", "FINISHED");
    dump.action(registered[1], "2025-03-05 09:00:00", "ASSESSMENT", "PHQ9", "COMPLETED");
    // Week of Mon 10 Mar: nothing.
    // Week of Mon 28 Apr: a post-discharge Action.
    dump.action(discharged[0], "2025-05-01 09:00:00", "PROGRAMME", "FATIGUE", "STARTED");
    // Sunday 29 June 23:30 UTC is Monday 30 June 00:30 in London: week of 30 June, not 23 June.
    dump.action(registered[2], "2025-06-29 23:30:00", "DIARY", "BLADDER", "STARTED");
    // A suspect timestamp, recorded at the moment of extraction: week of Mon 12 Jan 2026.
    dump.action(registered[3], TestDump.EXTRACTED_AT, "PROGRAMME", "FATIGUE", "FINISHED");

    loader.load(dump.write(dir));
  }

  @AfterEach
  void emptyTheDatabase() {
    jdbc.execute("truncate action, patient restart identity");
  }

  @Test
  void currentStatusBucketsAreMutuallyExclusive() throws Exception {
    mvc.perform(get("/api/dashboard/status"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.invited").value(3))
        .andExpect(jsonPath("$.registered").value(80))
        .andExpect(jsonPath("$.discharged").value(17))
        .andExpect(jsonPath("$.total").value(100));
  }

  @Test
  void theFunnelIsStrictAndFootnotesTheSkippedRegistration() throws Exception {
    mvc.perform(get("/api/dashboard/funnel"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.invited").value(100))
        .andExpect(jsonPath("$.registered").value(96))
        .andExpect(jsonPath("$.discharged").value(16))
        .andExpect(jsonPath("$.invitedToRegistered").value(0.96))
        .andExpect(jsonPath("$.registeredToDischarged").value(16.0 / 96))
        .andExpect(jsonPath("$.skippedRegistration").value(1))
        .andExpect(jsonPath("$.invitedToRegisteredTime.seconds").value(2 * 86400))
        .andExpect(jsonPath("$.invitedToRegisteredTime.n").value(96))
        .andExpect(jsonPath("$.registeredToDischargedTime.seconds").value(83 * 86400))
        .andExpect(jsonPath("$.registeredToDischargedTime.n").value(16));
  }

  @Test
  void patientListFiltersAreIgnored() throws Exception {
    mvc.perform(get("/api/dashboard/status").param("status", "INVITED").param("q", "nobody"))
        .andExpect(jsonPath("$.total").value(100))
        .andExpect(jsonPath("$.registered").value(80));
    mvc.perform(get("/api/dashboard/funnel").param("module", "DIARY"))
        .andExpect(jsonPath("$.invited").value(100));
  }

  @Test
  void weeklyActiveDefaultsToTheWeeksOfTheFirstAndLastAction() throws Exception {
    mvc.perform(get("/api/dashboard/weekly-active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2025-03-03"))
        .andExpect(jsonPath("$.to").value("2026-01-12"))
        .andExpect(jsonPath("$.weeks", hasSize(46)))
        .andExpect(jsonPath("$.weeks[0].weekStart").value("2025-03-03"))
        .andExpect(jsonPath("$.weeks[0].isoWeek").value("2025-W10"))
        .andExpect(jsonPath("$.weeks[0].activePatients").value(2))
        .andExpect(jsonPath("$.weeks[1].weekStart").value("2025-03-10"))
        .andExpect(jsonPath("$.weeks[1].activePatients").value(0))
        // Post-discharge Action, week of 28 April.
        .andExpect(jsonPath("$.weeks[8].weekStart").value("2025-04-28"))
        .andExpect(jsonPath("$.weeks[8].activePatients").value(1))
        // Suspect timestamp, last week.
        .andExpect(jsonPath("$.weeks[45].activePatients").value(1));
  }

  @Test
  void anActionJustAfterMidnightUtcDuringBstCountsInTheNextLondonWeek() throws Exception {
    mvc.perform(get("/api/dashboard/weekly-active").param("from", "2025-06-23").param("to", "2025-06-30"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks[*].weekStart", contains("2025-06-23", "2025-06-30")))
        .andExpect(jsonPath("$.weeks[*].activePatients", contains(0, 1)));
  }

  @Test
  void aChosenRangeSnapsToWholeWeeksAndShowsEmptyWeeks() throws Exception {
    mvc.perform(get("/api/dashboard/weekly-active").param("from", "2025-03-05").param("to", "2025-03-12"))
        .andExpect(jsonPath("$.from").value("2025-03-03"))
        .andExpect(jsonPath("$.to").value("2025-03-10"))
        .andExpect(jsonPath("$.weeks[*].activePatients", contains(2, 0)));
  }

  @Test
  void anInvalidRangeIsA400ProblemDetail() throws Exception {
    mvc.perform(get("/api/dashboard/weekly-active").param("from", "2025-13-01"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.errors[0].parameter").value("from"));
    mvc.perform(get("/api/dashboard/weekly-active").param("from", "2025-03-10").param("to", "2025-03-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].parameter").value("to"));
    mvc.perform(get("/api/dashboard/weekly-active").param("from", "2000-01-01").param("to", "2025-01-01"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].parameter").value("to"))
        .andExpect(jsonPath("$.errors[0].message").value("the range must cover at most 520 weeks"));
  }

  @Test
  void aFilledInEndIsPulledInToFitTheCap() throws Exception {
    mvc.perform(get("/api/dashboard/weekly-active").param("from", "1900-01-01"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("1900-01-01"))
        .andExpect(jsonPath("$.to").value("1909-12-13"))
        .andExpect(jsonPath("$.weeks", hasSize(520)));
  }

  /** Actions spanning more than the cap: the default range and the page's fallback must still work. */
  @Test
  void actionsSpanningMoreThanTheCapStillShowTheMostRecentWeeks() throws Exception {
    TestDump older = new TestDump();
    PatientRow early = older.patient("Early", "Adopter", "2009-12-01 10:00:00");
    early.registered = "2009-12-02 10:00:00";
    older.action(early, "2010-01-04 09:00:00", "DIARY", "BLADDER", "STARTED");
    loader.load(older.write(dir));

    mvc.perform(get("/api/dashboard/weekly-active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value("2016-02-01"))
        .andExpect(jsonPath("$.to").value("2026-01-12"))
        .andExpect(jsonPath("$.weeks", hasSize(520)));
    mvc.perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("value=\"2016-02-01\"")));
    mvc.perform(get("/dashboard").param("from", "nonsense"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("The date range was not valid")))
        .andExpect(content().string(containsString("value=\"2016-02-01\"")));
  }

  @Test
  void anEmptyDatasetGivesZerosAndNoWeeks() throws Exception {
    emptyTheDatabase();
    mvc.perform(get("/api/dashboard/status")).andExpect(jsonPath("$.total").value(0));
    mvc.perform(get("/api/dashboard/funnel"))
        .andExpect(jsonPath("$.invited").value(0))
        .andExpect(jsonPath("$.invitedToRegistered", is(nullValue())))
        .andExpect(jsonPath("$.invitedToRegisteredTime.seconds", is(nullValue())))
        .andExpect(jsonPath("$.invitedToRegisteredTime.n").value(0));
    mvc.perform(get("/api/dashboard/weekly-active"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.weeks", is(empty())));
    mvc.perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("No Actions have been recorded.")));
  }

  @Test
  void thePageShowsTheFiguresAndLabelsTheBucketsAsCurrentStatus() throws Exception {
    mvc.perform(get("/dashboard"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Patients by current status")))
        .andExpect(content().string(containsString("Current status:")))
        .andExpect(content().string(containsString("discharged without registering")))
        .andExpect(content().string(containsString("96%")))
        .andExpect(content().string(containsString("(n = <span>96</span>)")))
        .andExpect(content().string(containsString("83.0 days")))
        .andExpect(content().string(containsString("2025-W10 · week of 3 Mar 2025: 2 patients")));
  }

  @Test
  void thePageKeepsTheRangeInTheUrlAndShowsErrorsWithTheDefaultRange() throws Exception {
    mvc.perform(get("/dashboard").param("from", "2025-03-05").param("to", "2025-03-12"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("value=\"2025-03-05\"")))
        .andExpect(content().string(containsString("/api/dashboard/weekly-active?from=2025-03-05&amp;to=2025-03-12")));
    mvc.perform(get("/dashboard").param("from", "nonsense"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string(containsString("The date range was not valid")))
        .andExpect(content().string(containsString("value=\"2025-03-03\"")));
  }
}
