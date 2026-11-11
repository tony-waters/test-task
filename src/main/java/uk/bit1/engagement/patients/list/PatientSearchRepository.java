package uk.bit1.engagement.patients.list;

import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.bit1.engagement.core.Patient;

/** The patient list's repository: the {@link PatientSearch} fragment and nothing else. */
@Transactional(readOnly = true)
public interface PatientSearchRepository extends Repository<Patient, Long>, PatientSearch {}
