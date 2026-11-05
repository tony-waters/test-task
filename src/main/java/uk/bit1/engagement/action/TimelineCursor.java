package uk.bit1.engagement.action;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import uk.bit1.engagement.web.ParameterError;

/**
 * Where a timeline page ends: the last Action's recorded time and id. The next page starts
 * strictly after it in (recorded time, id) descending order. Clients treat the encoded form as
 * opaque.
 */
public record TimelineCursor(Instant recordedAt, long id) {

  public static final String PARAMETER = "cursor";

  private static final String VERSION = "v1";
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  public String encode() {
    String plain = VERSION + "|" + recordedAt + "|" + id;
    return ENCODER.encodeToString(plain.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Decodes a cursor from a request. Returns null when it is absent or blank, and also when it is
   * invalid, in which case an error is added to {@code errors}.
   */
  public static TimelineCursor decode(String value, List<ParameterError> errors) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      String plain = new String(DECODER.decode(value.trim()), StandardCharsets.UTF_8);
      String[] parts = plain.split("\\|", -1);
      if (parts.length == 3 && parts[0].equals(VERSION)) {
        long id = Long.parseLong(parts[2]);
        if (id > 0) {
          return new TimelineCursor(Instant.parse(parts[1]), id);
        }
      }
    } catch (IllegalArgumentException | DateTimeException e) {
      // Not one of ours. NumberFormatException is an IllegalArgumentException.
    }
    errors.add(new ParameterError(PARAMETER, "is not a cursor this API returned"));
    return null;
  }
}
