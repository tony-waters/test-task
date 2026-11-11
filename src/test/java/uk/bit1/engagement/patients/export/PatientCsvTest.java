package uk.bit1.engagement.patients.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import uk.bit1.engagement.core.Status;
import uk.bit1.engagement.patients.list.PatientSummary;

class PatientCsvTest {

  @Test
  void quotesWhatNeedsQuotingAndDoublesQuotes() {
    assertThat(PatientCsv.escape("plain")).isEqualTo("plain");
    assertThat(PatientCsv.escape("O'Brien, Jr")).isEqualTo("\"O'Brien, Jr\"");
    assertThat(PatientCsv.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
    assertThat(PatientCsv.escape("two\nlines")).isEqualTo("\"two\nlines\"");
    assertThat(PatientCsv.escape(null)).isEmpty();
  }

  @Test
  void neutralisesSpreadsheetFormulas() {
    assertThat(PatientCsv.escape("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    assertThat(PatientCsv.escape("+1")).isEqualTo("'+1");
    assertThat(PatientCsv.escape("-1")).isEqualTo("'-1");
    assertThat(PatientCsv.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
  }

  @Test
  void writesLondonTimesAndBlanksForUnknowns() {
    StringWriter out = new StringWriter();
    long rows = PatientCsv.write(Stream.of(eve()), out, new PatientCsv.RowCounter());

    assertThat(rows).isEqualTo(1);
    assertThat(out.toString())
        .isEqualTo(
            String.join(",", PatientCsv.HEADER)
                + "\r\n"
                + "00000000-0000-4000-8000-000000000005,,Eve,Midnight,,,,9990000034,,INVITED,"
                + "2025-07-01T00:30:00+01:00,,,\r\n");
  }

  /** Rows reach the writer as the source produces them; nothing is collected first. */
  @Test
  void writesEachRowBeforeReadingTheNext() {
    StringWriter out = new StringWriter();
    AtomicInteger produced = new AtomicInteger();
    Stream<PatientSummary> source =
        Stream.generate(
                () -> {
                  // Header plus every row produced so far is already written.
                  long linesWritten = out.toString().chars().filter(c -> c == '\n').count();
                  assertThat(linesWritten).isEqualTo(1 + produced.getAndIncrement());
                  return eve();
                })
            .limit(5);

    assertThat(PatientCsv.write(source, out, new PatientCsv.RowCounter())).isEqualTo(5);
  }

  private static PatientSummary eve() {
    return new PatientSummary(
        UUID.fromString("00000000-0000-4000-8000-000000000005"), null, "Eve", "Midnight", null,
        (LocalDate) null, null, "9990000034", null, Status.INVITED,
        Instant.parse("2025-06-30T23:30:00Z"), null, null, null);
  }
}
