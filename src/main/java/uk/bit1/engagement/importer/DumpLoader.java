package uk.bit1.engagement.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads Patients and Actions from a pg_dump file into the schema the migrations own (ADR 0002).
 *
 * <ul>
 *   <li>Only the {@code patient} and {@code action} COPY blocks are read.
 *   <li>Naive source timestamps are taken as UTC (ADR 0001).
 *   <li>Rows are upserted by UUID, so a second run changes nothing. Nothing is ever deleted.
 *   <li>Every row is kept: post-discharge Actions, look-alike duplicates and suspect timestamps
 *       included.
 *   <li>The dump's {@code entity_id} is used only to resolve each Action's Patient.
 * </ul>
 *
 * Only counts are logged, never row contents.
 */
@Service
public class DumpLoader {

  private static final Logger log = LoggerFactory.getLogger(DumpLoader.class);
  private static final int BATCH_SIZE = 500;

  private final JdbcTemplate jdbc;

  public DumpLoader(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional
  public LoadResult load(Path file) {
    Map<String, CopyBlock> blocks = parse(file);
    CopyBlock patients = require(blocks, "patient");
    CopyBlock actions = require(blocks, "action");
    log.info(
        "Parsed dump: {} patient rows, {} action rows",
        patients.rows().size(),
        actions.rows().size());

    LoadCounts patientCounts = upsertPatients(patients.namedRows());
    LoadCounts actionCounts = upsertActions(actions.namedRows(), patientUuidsByEntityId(patients));
    LoadResult result = new LoadResult(patientCounts, actionCounts);
    log.info(
        "Loaded patients: {} inserted, {} updated, {} unchanged",
        patientCounts.inserted(),
        patientCounts.updated(),
        patientCounts.unchanged());
    log.info(
        "Loaded actions: {} inserted, {} updated, {} unchanged",
        actionCounts.inserted(),
        actionCounts.updated(),
        actionCounts.unchanged());
    return result;
  }

  private static Map<String, CopyBlock> parse(Path file) {
    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      return CopyBlockParser.parse(reader);
    } catch (IOException e) {
      throw new UncheckedIOException("Could not read the dump file", e);
    }
  }

  private static CopyBlock require(Map<String, CopyBlock> blocks, String table) {
    CopyBlock block = blocks.get(table);
    if (block == null) {
      throw new DumpFormatException("the dump has no COPY block for table " + table);
    }
    return block;
  }

  private static Map<String, UUID> patientUuidsByEntityId(CopyBlock patients) {
    Map<String, UUID> byEntityId = new HashMap<>();
    for (CopyBlock.Row row : patients.namedRows()) {
      byEntityId.put(row.get("entity_id"), row.uuid("id"));
    }
    return byEntityId;
  }

  private LoadCounts upsertPatients(List<CopyBlock.Row> rows) {
    jdbc.execute(
        """
        create temporary table stage_patient (
            uuid uuid, title varchar(255), given_name varchar(255), family_name varchar(255),
            gender varchar(255), date_of_birth date, nhs_number varchar(255),
            hospital_id varchar(255), invited_at timestamptz, registered_at timestamptz,
            discharged_at timestamptz, source_created_at timestamptz
        ) on commit drop
        """);
    jdbc.batchUpdate(
        "insert into stage_patient values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
        BATCH_SIZE,
        (ps, row) -> {
          ps.setObject(1, row.uuid("id"));
          ps.setString(2, row.get("title"));
          ps.setString(3, row.get("given_name"));
          ps.setString(4, row.get("family_name"));
          ps.setString(5, row.get("gender"));
          ps.setObject(6, row.date("date_of_birth"));
          ps.setString(7, row.get("nhs_number"));
          ps.setString(8, row.get("hospital_id"));
          ps.setObject(9, row.utc("when_invited"));
          ps.setObject(10, row.utc("when_registered"));
          ps.setObject(11, row.utc("when_discharged"));
          ps.setObject(12, row.utc("entity_created"));
        });
    return upsert(
        rows.size(),
        """
        insert into patient as t (uuid, title, given_name, family_name, gender, date_of_birth,
                                  nhs_number, hospital_id, invited_at, registered_at,
                                  discharged_at, source_created_at)
        select uuid, title, given_name, family_name, gender, date_of_birth, nhs_number,
               hospital_id, invited_at, registered_at, discharged_at, source_created_at
          from stage_patient
        on conflict (uuid) do update set
            title = excluded.title, given_name = excluded.given_name,
            family_name = excluded.family_name, gender = excluded.gender,
            date_of_birth = excluded.date_of_birth, nhs_number = excluded.nhs_number,
            hospital_id = excluded.hospital_id, invited_at = excluded.invited_at,
            registered_at = excluded.registered_at, discharged_at = excluded.discharged_at,
            source_created_at = excluded.source_created_at
        where (t.title, t.given_name, t.family_name, t.gender, t.date_of_birth, t.nhs_number,
               t.hospital_id, t.invited_at, t.registered_at, t.discharged_at,
               t.source_created_at)
              is distinct from
              (excluded.title, excluded.given_name, excluded.family_name, excluded.gender,
               excluded.date_of_birth, excluded.nhs_number, excluded.hospital_id,
               excluded.invited_at, excluded.registered_at, excluded.discharged_at,
               excluded.source_created_at)
        """);
  }

  private LoadCounts upsertActions(List<CopyBlock.Row> rows, Map<String, UUID> patientUuids) {
    long unresolved =
        rows.stream().filter(r -> !patientUuids.containsKey(r.get("patient_entity_id"))).count();
    if (unresolved > 0) {
      throw new DumpFormatException(unresolved + " action rows reference no patient in the dump");
    }
    jdbc.execute(
        """
        create temporary table stage_action (
            uuid uuid, patient_uuid uuid, recorded_at timestamptz, module varchar(32),
            topic varchar(64), action_type varchar(32), source_created_at timestamptz
        ) on commit drop
        """);
    jdbc.batchUpdate(
        "insert into stage_action values (?, ?, ?, ?, ?, ?, ?)",
        rows,
        BATCH_SIZE,
        (ps, row) -> {
          ps.setObject(1, row.uuid("id"));
          ps.setObject(2, patientUuids.get(row.get("patient_entity_id")));
          ps.setObject(3, row.utc("when_recorded"));
          ps.setString(4, row.get("module_id"));
          ps.setString(5, row.get("context"));
          ps.setString(6, row.get("activity"));
          ps.setObject(7, row.utc("entity_created"));
        });
    return upsert(
        rows.size(),
        """
        insert into action as t (uuid, patient_id, recorded_at, module, topic, action_type,
                                 source_created_at)
        select s.uuid, p.id, s.recorded_at, s.module, s.topic, s.action_type, s.source_created_at
          from stage_action s
          join patient p on p.uuid = s.patient_uuid
        on conflict (uuid) do update set
            patient_id = excluded.patient_id, recorded_at = excluded.recorded_at,
            module = excluded.module, topic = excluded.topic,
            action_type = excluded.action_type, source_created_at = excluded.source_created_at
        where (t.patient_id, t.recorded_at, t.module, t.topic, t.action_type,
               t.source_created_at)
              is distinct from
              (excluded.patient_id, excluded.recorded_at, excluded.module, excluded.topic,
               excluded.action_type, excluded.source_created_at)
        """);
  }

  /**
   * Runs an {@code insert ... on conflict do update ... where changed} and counts what it did. A
   * freshly inserted row has {@code xmax = 0}; an updated one does not; unchanged rows are not
   * returned at all.
   */
  private LoadCounts upsert(long staged, String upsertSql) {
    return jdbc.queryForObject(
        "with upserted as (" + upsertSql + " returning (xmax = 0) as inserted) "
            + "select count(*) filter (where inserted), count(*) filter (where not inserted) "
            + "from upserted",
        (rs, n) -> {
          long inserted = rs.getLong(1);
          long updated = rs.getLong(2);
          return new LoadCounts(inserted, updated, staged - inserted - updated);
        });
  }
}
