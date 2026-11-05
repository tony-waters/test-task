package uk.bit1.engagement.importer;

/** What an upsert did to one table. */
public record LoadCounts(long inserted, long updated, long unchanged) {

  public long total() {
    return inserted + updated + unchanged;
  }
}
