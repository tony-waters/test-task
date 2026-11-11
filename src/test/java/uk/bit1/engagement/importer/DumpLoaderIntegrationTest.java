package uk.bit1.engagement.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.bit1.engagement.domain.patient.Status;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;
import uk.bit1.engagement.support.TestDump;

@IntegrationTest
class DumpLoaderIntegrationTest {

  @Autowired DumpLoader loader;
  @Autowired JdbcTemplate jdbc;
  @TempDir Path dir;

  @BeforeEach
  @AfterEach
  void emptyTheDatabase() {
    jdbc.execute("truncate action, patient restart identity");
  }

  @Test
  void firstRunInsertsEveryRow() {
    TestDump dump = StandardData.build();

    LoadResult result = loader.load(dump.write(dir));

    assertThat(result.patients()).isEqualTo(new LoadCounts(StandardData.PATIENTS, 0, 0));
    assertThat(result.actions()).isEqualTo(new LoadCounts(dump.actions().size(), 0, 0));
  }

  @Test
  void secondRunChangesNothing() {
    Path file = StandardData.build().write(dir);
    loader.load(file);
    List<Map<String, Object>> patientsBefore = snapshot("patient");
    List<Map<String, Object>> actionsBefore = snapshot("action");

    LoadResult second = loader.load(file);

    assertThat(second.patients()).isEqualTo(new LoadCounts(0, 0, StandardData.PATIENTS));
    assertThat(second.actions().inserted()).isZero();
    assertThat(second.actions().updated()).isZero();
    assertThat(snapshot("patient")).isEqualTo(patientsBefore);
    assertThat(snapshot("action")).isEqualTo(actionsBefore);
  }

  @Test
  void aChangedRowIsUpdatedInPlace() {
    TestDump dump = StandardData.build();
    loader.load(dump.write(dir));
    Long idBefore = idOf(StandardData.BERT);

    TestDump.PatientRow bert =
        dump.patients().stream().filter(p -> p.uuid.equals(StandardData.BERT)).findFirst().orElseThrow();
    bert.registered = "2025-04-11 10:00:00";
    LoadResult result = loader.load(dump.write(dir));

    assertThat(result.patients()).isEqualTo(new LoadCounts(0, 1, StandardData.PATIENTS - 1));
    assertThat(idOf(StandardData.BERT)).isEqualTo(idBefore);
    assertThat(statusOf(StandardData.BERT)).isEqualTo("REGISTERED");
  }

  @Test
  void neverDeletesRowsMissingFromTheFile() {
    loader.load(StandardData.build().write(dir));
    TestDump smaller = new TestDump();
    smaller.patient("Only", "One", "2025-01-01 00:00:00");

    loader.load(smaller.write(dir));

    assertThat(count("patient")).isEqualTo(StandardData.PATIENTS + 1);
  }

  @Test
  void keepsEveryActionIncludingLookAlikesAndPostDischarge() {
    TestDump dump = StandardData.build();

    loader.load(dump.write(dir));

    assertThat(count("action")).isEqualTo(dump.actions().size());
    assertThat(
            jdbc.queryForObject(
                "select count(*) from action where uuid in (?, ?)",
                Long.class,
                StandardData.DUPLICATE_A,
                StandardData.DUPLICATE_B))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from action a join patient p on p.id = a.patient_id"
                    + " where a.recorded_at > p.discharged_at",
                Long.class))
        .isEqualTo(1);
  }

  @Test
  void actionsBelongToThePatientNamedByTheDumpsEntityId() {
    loader.load(StandardData.build().write(dir));

    assertThat(
            jdbc.queryForObject(
                "select count(*) from action a join patient p on p.id = a.patient_id where p.uuid = ?",
                Long.class,
                StandardData.DAN))
        .isEqualTo(4);
  }

  @Test
  void readsNaiveTimestampsAsUtc() {
    loader.load(StandardData.build().write(dir));

    Instant invited =
        jdbc.queryForObject(
            "select invited_at from patient where uuid = ?", Instant.class, StandardData.EVE);

    assertThat(invited).isEqualTo(Instant.parse("2025-06-30T23:30:00Z"));
  }

  @Test
  void generatedStatusAgreesWithTheJavaRule() {
    loader.load(StandardData.build().write(dir));

    List<Map<String, Object>> rows =
        jdbc.queryForList("select registered_at, discharged_at, status from patient");

    assertThat(rows).hasSize(StandardData.PATIENTS);
    for (Map<String, Object> row : rows) {
      Instant registered = toInstant(row.get("registered_at"));
      Instant discharged = toInstant(row.get("discharged_at"));
      assertThat(row.get("status")).isEqualTo(Status.derive(registered, discharged).name());
    }
    assertThat(statusOf(StandardData.CORA)).isEqualTo("DISCHARGED");
  }

  @Test
  void aMalformedDateOfBirthFailsTheLoadWithoutLoggingTheValue() {
    TestDump dump = StandardData.build();
    dump.patients().get(1).dateOfBirth = "1980-13-15";

    Throwable failure = catchThrowable(() -> loader.load(dump.write(dir)));

    assertThat(failure)
        .isInstanceOf(DumpFormatException.class)
        .hasMessage("COPY patient row 2, column date_of_birth: not a date")
        .hasNoCause();
    assertThat(count("patient")).isZero();
  }

  private static Instant toInstant(Object value) {
    return value == null ? null : ((java.sql.Timestamp) value).toInstant();
  }

  private List<Map<String, Object>> snapshot(String table) {
    return jdbc.queryForList("select * from " + table + " order by uuid");
  }

  private long count(String table) {
    return jdbc.queryForObject("select count(*) from " + table, Long.class);
  }

  private Long idOf(UUID uuid) {
    return jdbc.queryForObject("select id from patient where uuid = ?", Long.class, uuid);
  }

  private String statusOf(UUID uuid) {
    return jdbc.queryForObject("select status from patient where uuid = ?", String.class, uuid);
  }
}
