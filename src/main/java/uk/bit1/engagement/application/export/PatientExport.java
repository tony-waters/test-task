package uk.bit1.engagement.application.export;

import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.application.patient.PatientQueryService;
import uk.bit1.engagement.application.patient.query.PatientListQuery;
import uk.bit1.engagement.application.patient.query.PatientSummary;

/**
 * The CSV export of the patient list. Streams from one read-only transaction through a database
 * cursor, and writes one audit line per export: who, which filters, how many rows. The audit line
 * never contains identifiers, and the search term appears only as its length.
 */
@Service
public class PatientExport {

  private static final Logger audit = LoggerFactory.getLogger("uk.bit1.engagement.audit");

  private final PatientQueryService patients;
  private final int fetchSize;

  PatientExport(PatientQueryService patients, @Value("${app.export.fetch-size}") int fetchSize) {
    this.patients = patients;
    this.fetchSize = fetchSize;
  }

  /**
   * Writes every Patient matching the query's search and filters, in its sort order, ignoring its
   * page. Returns the number of rows.
   */
  @Transactional(readOnly = true)
  public long write(PatientListQuery query, Writer out, String user) {
    PatientCsv.RowCounter counter = new PatientCsv.RowCounter();
    boolean complete = false;
    try (Stream<PatientSummary> rows = patients.stream(query, fetchSize)) {
      long count = PatientCsv.write(rows, out, counter);
      complete = true;
      return count;
    } finally {
      audit
          .atInfo()
          .setMessage("patient export")
          .addKeyValue("user", user)
          .addKeyValue("filters", summary(query))
          .addKeyValue("rows", counter.count())
          .addKeyValue("complete", complete)
          .log();
    }
  }

  /** The query without identifiers: the search term becomes its length. */
  static Map<String, Object> summary(PatientListQuery query) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("searchLength", query.search() == null ? 0 : query.search().length());
    summary.put("status", query.statuses().stream().map(Enum::name).toList());
    summary.put("module", query.modules().stream().map(Enum::name).toList());
    summary.put("invitedFrom", query.invitedFrom() == null ? null : query.invitedFrom().toString());
    summary.put("invitedTo", query.invitedTo() == null ? null : query.invitedTo().toString());
    summary.put("sort", query.sort().param() + " " + query.direction().param());
    return summary;
  }
}
