package uk.bit1.engagement.patients.detail;

import java.time.Instant;
import uk.bit1.engagement.core.Module;

/** One Patient's Actions of one type in one Topic: how many, and the first and last time. */
record EngagementRow(
    Module module, String topic, String actionType, long count, Instant first, Instant last) {}
