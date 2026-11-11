package uk.bit1.engagement.application.patient.query;

import java.util.List;

/**
 * One page of a Patient's timeline, newest first.
 *
 * @param nextCursor pass as {@code cursor} for the next, older page; null on the last page
 */
public record TimelinePage(List<TimelineEntry> content, int size, String nextCursor) {}
