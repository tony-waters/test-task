package uk.bit1.engagement.importer;

/** What one run of the loader did. */
public record LoadResult(LoadCounts patients, LoadCounts actions) {}
