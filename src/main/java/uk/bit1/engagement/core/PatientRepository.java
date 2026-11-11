package uk.bit1.engagement.core;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import uk.bit1.engagement.shared.NotFoundException;

/**
 * Finds one Patient by UUID. The app never writes Patients: the loader does, through {@code
 * JdbcTemplate}, because its upsert needs {@code on conflict}. Each use case declares its own
 * queries in its own repository.
 */
public interface PatientRepository extends Repository<Patient, Long> {

  Optional<Patient> findByUuid(UUID uuid);

  /**
   * @throws NotFoundException when no Patient has that UUID
   */
  default Patient getByUuid(UUID uuid) {
    return findByUuid(uuid).orElseThrow(() -> new NotFoundException("No patient has that ID."));
  }
}
