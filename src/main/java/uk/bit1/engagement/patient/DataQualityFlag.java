package uk.bit1.engagement.patient;

/** Something about a Patient's data worth a second look. The data is kept as it is. */
public enum DataQualityFlag {
  /** Discharged without ever registering. */
  SKIPPED_REGISTRATION,
  /** At least one Action recorded at, or just before, the moment the data was extracted. */
  SUSPECT_TIMESTAMPS
}
