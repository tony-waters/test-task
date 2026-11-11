package uk.bit1.engagement.domain.action;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Loads and stores Action entities. The read side's queries live in {@code
 * application.patient.PatientQueryRepository}. Only the loader writes Actions today, through
 * {@code JdbcTemplate}.
 */
public interface ActionRepository extends JpaRepository<Action, Long> {}
