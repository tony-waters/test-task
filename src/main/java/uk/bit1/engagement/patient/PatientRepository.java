package uk.bit1.engagement.patient;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads Patients. The app never writes them: the loader does, through {@code JdbcTemplate},
 * because its upsert needs {@code on conflict}.
 */
public interface PatientRepository extends JpaRepository<Patient, Long>, PatientSearch {

  Optional<Patient> findByUuid(UUID uuid);
}
