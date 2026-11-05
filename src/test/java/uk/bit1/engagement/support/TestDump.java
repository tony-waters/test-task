package uk.bit1.engagement.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Builds a synthetic file in the shape of the supplied pg_dump: DDL and SET noise around two COPY
 * blocks, with the source's column names and order. Every value is invented. No real dump rows
 * are used in tests.
 */
public final class TestDump {

  public static final String EXTRACTED_AT = "2026-01-16 13:00:00.000001";

  private final List<PatientRow> patients = new ArrayList<>();
  private final List<ActionRow> actions = new ArrayList<>();
  private long nextEntityId = 1;

  /** A source patient row. Timestamps are naive, in the dump's text form. */
  public static final class PatientRow {
    public final long entityId;
    public UUID uuid = UUID.randomUUID();
    public String title = "Mx.";
    public String givenName;
    public String familyName;
    public String gender;
    public String dateOfBirth;
    public String nhsNumber;
    public String hospitalId;
    public String invited;
    public String registered;
    public String discharged;

    PatientRow(long entityId) {
      this.entityId = entityId;
    }
  }

  public record ActionRow(
      long entityId, UUID uuid, long patientEntityId, String recorded, String activity,
      String context, String module) {}

  public PatientRow patient(String givenName, String familyName, String invited) {
    PatientRow row = new PatientRow(nextEntityId++);
    row.givenName = givenName;
    row.familyName = familyName;
    row.invited = invited;
    patients.add(row);
    return row;
  }

  public ActionRow action(
      PatientRow patient, String recorded, String module, String context, String activity) {
    return action(patient, UUID.randomUUID(), recorded, module, context, activity);
  }

  public ActionRow action(
      PatientRow patient, UUID uuid, String recorded, String module, String context,
      String activity) {
    ActionRow row =
        new ActionRow(nextEntityId++, uuid, patient.entityId, recorded, activity, context, module);
    actions.add(row);
    return row;
  }

  public List<PatientRow> patients() {
    return patients;
  }

  public List<ActionRow> actions() {
    return actions;
  }

  public String render() {
    StringBuilder out = new StringBuilder();
    out.append("--\n-- Synthetic test data in pg_dump shape\n--\n\n");
    out.append("SET client_encoding = 'UTF8';\n");
    out.append("CREATE TABLE public.action (\n    entity_id bigint NOT NULL\n);\n\n");
    out.append(
        "COPY public.action (entity_created, entity_id, entity_updated, entity_version,"
            + " patient_entity_id, when_recorded, id, activity, context, module_id) FROM stdin;\n");
    for (ActionRow a : actions) {
      line(
          out, EXTRACTED_AT, Long.toString(a.entityId()), EXTRACTED_AT, "0",
          Long.toString(a.patientEntityId()), a.recorded(), a.uuid().toString(), a.activity(),
          a.context(), a.module());
    }
    out.append("\\.\n\n");
    out.append("CREATE TABLE public.patient (\n    entity_id bigint NOT NULL\n);\n\n");
    out.append(
        "COPY public.patient (date_of_birth, entity_created, entity_id, entity_updated,"
            + " entity_version, when_discharged, when_invited, when_registered, id, family_name,"
            + " gender, given_name, hospital_id, nhs_number, title) FROM stdin;\n");
    for (PatientRow p : patients) {
      line(
          out, p.dateOfBirth, EXTRACTED_AT, Long.toString(p.entityId), EXTRACTED_AT, "0",
          p.discharged, p.invited, p.registered, p.uuid.toString(), p.familyName, p.gender,
          p.givenName, p.hospitalId, p.nhsNumber, p.title);
    }
    out.append("\\.\n\n");
    out.append("ALTER TABLE ONLY public.patient ADD CONSTRAINT patient_pkey PRIMARY KEY (entity_id);\n");
    return out.toString();
  }

  public Path write(Path dir) {
    try {
      Path file = Files.createTempFile(dir, "test-dump", ".sql");
      Files.writeString(file, render(), StandardCharsets.UTF_8);
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void line(StringBuilder out, String... fields) {
    for (int i = 0; i < fields.length; i++) {
      if (i > 0) {
        out.append('\t');
      }
      out.append(fields[i] == null ? "\\N" : fields[i]);
    }
    out.append('\n');
  }
}
