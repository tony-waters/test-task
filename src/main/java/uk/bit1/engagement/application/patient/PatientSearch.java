package uk.bit1.engagement.application.patient;

import java.util.stream.Stream;
import uk.bit1.engagement.application.patient.query.PatientListQuery;
import uk.bit1.engagement.application.patient.query.PatientPage;
import uk.bit1.engagement.application.patient.query.PatientSummary;

/** The patient list query, as a Spring Data fragment of {@link PatientQueryRepository}. */
public interface PatientSearch {

  /** One page of the list. Costs two statements, the rows and the count, whatever the size. */
  PatientPage search(PatientListQuery query);

  /**
   * Every row matching the query's search and filters, in its sort order, ignoring its page.
   * Read lazily through a cursor; close the stream, and call it inside a transaction.
   */
  Stream<PatientSummary> stream(PatientListQuery query, int fetchSize);
}
