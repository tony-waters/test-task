package uk.bit1.engagement.web.patient;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uk.bit1.engagement.application.common.InvalidParametersException;
import uk.bit1.engagement.application.common.ParameterError;
import uk.bit1.engagement.application.patient.query.TimelineCursor;

/**
 * A validated request for one Patient's detail or timeline.
 *
 * @param cursor where the previous timeline page ended, or null for the newest page
 */
public record PatientRequest(UUID id, TimelineCursor cursor) {

  /**
   * Parses the path's UUID and an optional cursor.
   *
   * @throws InvalidParametersException listing every invalid parameter
   */
  public static PatientRequest parse(String uuid, String cursor) {
    List<ParameterError> errors = new ArrayList<>();
    UUID id = parseUuid(uuid, errors);
    TimelineCursor decoded = TimelineCursor.decode(cursor, errors);
    if (!errors.isEmpty()) {
      throw new InvalidParametersException(errors);
    }
    return new PatientRequest(id, decoded);
  }

  /**
   * Only the canonical 36-character form. {@code UUID.fromString} also accepts shortened groups
   * such as {@code 1-1-1-1-1}, which would give one Patient many URLs.
   */
  private static UUID parseUuid(String value, List<ParameterError> errors) {
    if (value != null && value.length() == 36) {
      try {
        UUID id = UUID.fromString(value);
        if (id.toString().equalsIgnoreCase(value)) {
          return id;
        }
      } catch (IllegalArgumentException e) {
        // reported below
      }
    }
    errors.add(new ParameterError("uuid", "must be a UUID"));
    return null;
  }
}
