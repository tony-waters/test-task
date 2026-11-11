package uk.bit1.engagement.domain.patient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Loads and stores Patient entities. The read side's queries live in {@code
 * application.patient.PatientQueryRepository}. The app never writes Patients today: the loader
 * does, through {@code JdbcTemplate}, because its upsert needs {@code on conflict}.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {

  Optional<Patient> findByUuid(UUID uuid);
}
