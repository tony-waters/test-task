package uk.bit1.engagement.support;

import java.nio.file.Path;
import java.util.UUID;
import uk.bit1.engagement.support.TestDump.PatientRow;

/**
 * The synthetic population the list tests run against: 5 named Patients with the awkward shapes,
 * plus 25 filler Patients. 30 in all.
 */
public final class StandardData {

  public static final UUID ADA = UUID.fromString("00000000-0000-4000-8000-000000000001");
  public static final UUID BERT = UUID.fromString("00000000-0000-4000-8000-000000000002");
  public static final UUID CORA = UUID.fromString("00000000-0000-4000-8000-000000000003");
  public static final UUID DAN = UUID.fromString("00000000-0000-4000-8000-000000000004");
  public static final UUID EVE = UUID.fromString("00000000-0000-4000-8000-000000000005");
  public static final UUID DUPLICATE_A = UUID.fromString("00000000-0000-4000-9000-00000000000a");
  public static final UUID DUPLICATE_B = UUID.fromString("00000000-0000-4000-9000-00000000000b");

  public static final int PATIENTS = 30;
  public static final int FILLERS = 25;

  private static Path file;

  private StandardData() {}

  public static TestDump build() {
    TestDump dump = new TestDump();

    // Registered, with a Diary Action. Newest Invitation of the named five apart from Eve.
    PatientRow ada = dump.patient("Ada", "Testwell", "2025-03-01 10:00:00");
    ada.uuid = ADA;
    ada.gender = "FEMALE";
    ada.dateOfBirth = "1980-06-15";
    ada.nhsNumber = "9990000018";
    ada.hospitalId = "HOS000001";
    ada.registered = "2025-03-05 09:00:00";
    dump.action(ada, "2025-04-01 08:00:00", "DIARY", "BLADDER", "STARTED");

    // Invited only, nothing known: no date of birth, gender or NHS number, no Actions.
    PatientRow bert = dump.patient("Bert", "Sample", "2025-04-10 10:00:00");
    bert.uuid = BERT;
    bert.hospitalId = "HOS000002";

    // Skipped registration: discharged without ever registering. No Actions.
    PatientRow cora = dump.patient("Cora", "Skipped", "2025-02-01 10:00:00");
    cora.uuid = CORA;
    cora.gender = "FEMALE";
    cora.dateOfBirth = "1990-01-01";
    cora.nhsNumber = "9990000026";
    cora.discharged = "2025-06-01 10:00:00";

    // Discharged, with a post-discharge Action and two look-alike duplicates.
    PatientRow dan = dump.patient("Dan", "Leaver", "2025-01-10 10:00:00");
    dan.uuid = DAN;
    dan.gender = "MALE";
    dan.dateOfBirth = "1970-12-31";
    dan.hospitalId = "HOS000004";
    dan.registered = "2025-01-20 10:00:00";
    dan.discharged = "2025-05-01 10:00:00";
    dump.action(dan, "2025-04-01 09:00:00", "PROGRAMME", "FATIGUE", "STARTED");
    dump.action(dan, DUPLICATE_A, "2025-04-02 09:00:00", "PROGRAMME", "FATIGUE", "TASK-COMPLETED");
    dump.action(dan, DUPLICATE_B, "2025-04-02 09:00:00", "PROGRAMME", "FATIGUE", "TASK-COMPLETED");
    dump.action(dan, "2025-06-01 09:00:00", "PROGRAMME", "FATIGUE", "FINISHED");

    // Invited at 23:30 UTC on 30 June, which is 00:30 on 1 July in London (BST).
    PatientRow eve = dump.patient("Eve", "Midnight", "2025-06-30 23:30:00");
    eve.uuid = EVE;
    eve.dateOfBirth = "2000-11-05";
    eve.nhsNumber = "9990000034";
    eve.registered = "2025-07-02 10:00:00";
    dump.action(eve, "2026-01-16 13:00:00", "ASSESSMENT", "PHQ9", "COMPLETED");

    // Fillers: registered, invited during 2024, one Assessment each.
    for (int i = 1; i <= FILLERS; i++) {
      PatientRow filler =
          dump.patient("Filler", "Person%02d".formatted(i), "2024-%02d-10 12:00:00".formatted(1 + i % 12));
      filler.uuid = UUID.fromString("00000000-0000-4000-a000-%012d".formatted(i));
      filler.dateOfBirth = "19%02d-03-01".formatted(50 + i);
      filler.registered = "2024-12-20 12:00:00";
      dump.action(filler, "2025-01-0%d 12:00:00".formatted(1 + i % 9), "ASSESSMENT", "GAD-7", "COMPLETED");
    }
    return dump;
  }

  /** The standard dump, written once per test run to a temporary file. */
  public static synchronized Path file() {
    if (file == null) {
      try {
        Path dir = java.nio.file.Files.createTempDirectory("engagement-test");
        file = build().write(dir);
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException(e);
      }
    }
    return file;
  }
}
