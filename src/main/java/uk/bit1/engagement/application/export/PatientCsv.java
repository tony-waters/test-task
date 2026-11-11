package uk.bit1.engagement.application.export;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import uk.bit1.engagement.application.patient.query.PatientSummary;
import uk.bit1.engagement.domain.common.London;

/**
 * The patient list as RFC 4180 CSV: the list's columns, identifiers included. Times are London
 * local times with their offset. Rows are written as they are read, so a whole export is never in
 * memory.
 */
final class PatientCsv {

  static final List<String> HEADER =
      List.of(
          "id", "title", "given_name", "family_name", "gender", "date_of_birth", "age",
          "nhs_number", "hospital_id", "status", "invited_at", "registered_at", "discharged_at",
          "last_action_at");

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(London.ZONE);

  private PatientCsv() {}

  /**
   * Writes the header and every row, and returns the number of rows written.
   *
   * @throws UncheckedIOException if the output fails, for example when the client goes away
   */
  static long write(Stream<PatientSummary> rows, Writer out, RowCounter counter) {
    try {
      line(out, HEADER);
      Iterator<PatientSummary> it = rows.iterator();
      while (it.hasNext()) {
        line(out, fields(it.next()));
        counter.increment();
      }
      out.flush();
      return counter.count();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Counts rows as they are written, so a failed export can still report how far it got. */
  static final class RowCounter {
    private long count;

    void increment() {
      count++;
    }

    long count() {
      return count;
    }
  }

  private static List<String> fields(PatientSummary p) {
    return Arrays.asList(
        p.id().toString(),
        p.title(),
        p.givenName(),
        p.familyName(),
        p.gender(),
        p.dateOfBirth() == null ? null : p.dateOfBirth().toString(),
        p.age() == null ? null : p.age().toString(),
        p.nhsNumber(),
        p.hospitalId(),
        p.status().name(),
        time(p.invitedAt()),
        time(p.registeredAt()),
        time(p.dischargedAt()),
        time(p.lastActionAt()));
  }

  private static String time(Instant instant) {
    return instant == null ? null : TIME.format(instant);
  }

  private static void line(Writer out, List<String> fields) throws IOException {
    for (int i = 0; i < fields.size(); i++) {
      if (i > 0) {
        out.write(',');
      }
      out.write(escape(fields.get(i)));
    }
    out.write("\r\n");
  }

  /**
   * Quotes a field when it needs it. A value a spreadsheet would run as a formula (starting with
   * {@code = + - @}, tab or carriage return) gets a leading apostrophe, so opening the export
   * can't execute what someone typed into a name.
   */
  static String escape(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    String v = value;
    if ("=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
      v = "'" + v;
    }
    if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0
        || v.indexOf('\r') >= 0) {
      v = "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }
}
