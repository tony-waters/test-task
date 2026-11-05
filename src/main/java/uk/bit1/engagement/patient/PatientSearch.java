package uk.bit1.engagement.patient;

/** The patient list query, as a Spring Data fragment of {@link PatientRepository}. */
public interface PatientSearch {

  /** One page of the list. Costs two statements, the rows and the count, whatever the size. */
  PatientPage search(PatientListQuery query);
}
