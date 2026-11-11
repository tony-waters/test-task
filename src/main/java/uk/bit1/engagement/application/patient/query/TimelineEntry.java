package uk.bit1.engagement.application.patient.query;

import java.time.Instant;
import java.util.UUID;
import uk.bit1.engagement.domain.action.Module;

/**
 * One Action on a Patient's timeline.
 *
 * @param id the Action's UUID
 * @param postDischarge recorded after the Patient's Discharge
 * @param suspectTimestamp recorded at or just before the moment the data was extracted
 */
public record TimelineEntry(
    UUID id,
    Instant recordedAt,
    Module module,
    String topic,
    String actionType,
    boolean postDischarge,
    boolean suspectTimestamp) {}
