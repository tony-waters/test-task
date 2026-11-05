package uk.bit1.engagement.importer;

/** The dump file is not in the shape the loader expects. Messages never include row contents. */
public class DumpFormatException extends RuntimeException {
  public DumpFormatException(String message) {
    super(message);
  }
}
