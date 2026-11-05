package uk.bit1.engagement.action;

import java.time.Instant;

/** One Patient's Actions of one type in one Topic: how many, and the first and last time. */
public record EngagementRow(
    Module module, String topic, String actionType, long count, Instant first, Instant last) {}
