package uk.bit1.engagement.patients.detail;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import uk.bit1.engagement.core.Status;
import uk.bit1.engagement.patients.quality.DataQualityFlag;

/**
 * One Patient: who they are, where they are in the journey and how they have engaged.
 *
 * @param id the Patient's UUID, the only identifier exposed
 * @param age current age in whole years from today's London date, or null when the date of birth
 *     is unknown
 * @param gender as recorded, or null when not recorded
 */
public record PatientDetail(
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
    Set<DataQualityFlag> dataQualityFlags,
    EngagementSummary engagement) {}
