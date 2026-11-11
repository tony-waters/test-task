package uk.bit1.engagement.application.patient.query;

import java.time.Instant;
import uk.bit1.engagement.domain.action.Module;

/** One Patient's Actions of one type in one Topic: how many, and the first and last time. */
public record EngagementRow(
    Module module, String topic, String actionType, long count, Instant first, Instant last) {}
