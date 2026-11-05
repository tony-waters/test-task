package uk.bit1.engagement.patient;

import java.util.stream.Stream;

/** The patient list query, as a Spring Data fragment of {@link PatientRepository}. */
public interface PatientSearch {

  /** One page of the list. Costs two statements, the rows and the count, whatever the size. */
  PatientPage search(PatientListQuery query);

  /**
   * Every row matching the query's search and filters, in its sort order, ignoring its page.
   * Read lazily through a cursor; close the stream, and call it inside a transaction.
   */
  Stream<PatientSummary> stream(PatientListQuery query, int fetchSize);
}
