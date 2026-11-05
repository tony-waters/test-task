package uk.bit1.engagement.importer;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the {@code COPY ... FROM stdin} blocks from a pg_dump plain-text file. Every other line
 * (DDL, SET, comments, psql meta-commands) is skipped and never executed (ADR 0002).
 *
 * <p>Fields use PostgreSQL's COPY text format: tab-separated, {@code \N} for NULL, and backslash
 * escapes.
 */
final class CopyBlockParser {

  private static final Pattern COPY_HEADER =
      Pattern.compile("^COPY\\s+(?:[\\w\"]+\\.)?\"?(\\w+)\"?\\s*\\(([^)]*)\\)\\s+FROM\\s+stdin;\\s*$");
  private static final String END_OF_DATA = "\\.";
  private static final String NULL_MARKER = "\\N";

  private CopyBlockParser() {}

  /** All COPY blocks in the input, keyed by unqualified table name. */
  static Map<String, CopyBlock> parse(BufferedReader reader) throws IOException {
    Map<String, CopyBlock> blocks = new LinkedHashMap<>();
    String line;
    int lineNumber = 0;
    while ((line = reader.readLine()) != null) {
      lineNumber++;
      Matcher header = COPY_HEADER.matcher(line);
      if (!header.matches()) {
        continue;
      }
      String table = header.group(1);
      List<String> columns =
          Arrays.stream(header.group(2).split(","))
              .map(String::trim)
              .map(c -> c.replace("\"", ""))
              .toList();
      List<List<String>> rows = new ArrayList<>();
      boolean terminated = false;
      while ((line = reader.readLine()) != null) {
        lineNumber++;
        if (line.equals(END_OF_DATA)) {
          terminated = true;
          break;
        }
        List<String> fields = splitRow(line);
        if (fields.size() != columns.size()) {
          throw new DumpFormatException(
              "line %d of COPY %s has %d fields, expected %d"
                  .formatted(lineNumber, table, fields.size(), columns.size()));
        }
        rows.add(fields);
      }
      if (!terminated) {
        throw new DumpFormatException("COPY " + table + " block is not terminated");
      }
      blocks.put(table, new CopyBlock(table, columns, rows));
    }
    return blocks;
  }

  static List<String> splitRow(String line) {
    List<String> fields = new ArrayList<>();
    for (String raw : line.split("\t", -1)) {
      fields.add(raw.equals(NULL_MARKER) ? null : unescape(raw));
    }
    return fields;
  }

  static String unescape(String raw) {
    if (raw.indexOf('\\') < 0) {
      return raw;
    }
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c != '\\' || i + 1 == raw.length()) {
        out.append(c);
        continue;
      }
      char next = raw.charAt(++i);
      switch (next) {
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'v' -> out.append('\u000B');
        case 'x' -> {
          int end = i + 1;
          while (end < raw.length() && end < i + 3 && isHex(raw.charAt(end))) {
            end++;
          }
          if (end == i + 1) {
            out.append('x');
          } else {
            out.append((char) Integer.parseInt(raw.substring(i + 1, end), 16));
            i = end - 1;
          }
        }
        default -> {
          if (next >= '0' && next <= '7') {
            int end = i;
            while (end < raw.length() && end < i + 3 && raw.charAt(end) >= '0' && raw.charAt(end) <= '7') {
              end++;
            }
            out.append((char) Integer.parseInt(raw.substring(i, end), 8));
            i = end - 1;
          } else {
            out.append(next);
          }
        }
      }
    }
    return out.toString();
  }

  private static boolean isHex(char c) {
    return Character.digit(c, 16) >= 0;
  }
}
