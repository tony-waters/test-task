package uk.bit1.engagement.patient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the patient list.
 *
 * @param id the Patient's UUID, the only identifier exposed
 * @param age current age in whole years, or null when the date of birth is unknown
 * @param lastActionAt when the Patient last recorded an Action, or null if never
 */
public record PatientSummary(
    UUID id,
    String title,
    String givenName,
    String familyName,
    String gender,
    LocalDate dateOfBirth,
    Integer age,
    String nhsNumber,
    String hospitalId,
    Status status,
    Instant invitedAt,
    Instant registeredAt,
    Instant dischargedAt,
    Instant lastActionAt) {}
