package uk.bit1.engagement.importer;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The rows of one {@code COPY table (columns) FROM stdin} block, with fields already unescaped.
 * SQL NULL ({@code \N}) is a Java null.
 */
record CopyBlock(String table, List<String> columns, List<List<String>> rows) {

  /**
   * A view of one row keyed by column name, with typed accessors.
   *
   * <p>A value that does not parse raises {@link DumpFormatException} naming the table, row and
   * column, never the value. The JDK's own parse exceptions quote the input, which could be a date
   * of birth or another identifier, so they are not kept as the cause.
   *
   * @param number the row's 1-based position within its COPY block
   */
  record Row(String table, int number, Map<String, Integer> index, List<String> fields) {

    String get(String column) {
      Integer i = index.get(column);
      if (i == null) {
        throw new DumpFormatException("COPY " + table + " has no column " + column);
      }
      return fields.get(i);
    }

    UUID uuid(String column) {
      return parse(column, "a UUID", UUID::fromString);
    }

    LocalDate date(String column) {
      return parse(column, "a date", LocalDate::parse);
    }

    /** A naive source timestamp, interpreted as UTC (ADR 0001). */
    OffsetDateTime utc(String column) {
      return parse(column, "a timestamp", CopyBlock::utc);
    }

    private <T> T parse(String column, String expected, Function<String, T> parser) {
      String value = get(column);
      if (value == null) {
        return null;
      }
      try {
        return parser.apply(value);
      } catch (RuntimeException e) {
        throw new DumpFormatException(
            "COPY %s row %d, column %s: not %s".formatted(table, number, column, expected));
      }
    }
  }

  List<Row> namedRows() {
    Map<String, Integer> index = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      index.put(columns.get(i), i);
    }
    List<Row> named = new java.util.ArrayList<>(rows.size());
    for (int r = 0; r < rows.size(); r++) {
      named.add(new Row(table, r + 1, index, rows.get(r)));
    }
    return named;
  }

  /** Parses a naive {@code yyyy-mm-dd hh:mm:ss[.ffffff]} timestamp as UTC. */
  static OffsetDateTime utc(String value) {
    return LocalDateTime.parse(value.replace(' ', 'T')).atOffset(ZoneOffset.UTC);
  }
}
