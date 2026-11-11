package uk.bit1.engagement.application.patient.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uk.bit1.engagement.application.common.ParameterError;

class TimelineCursorTest {

  @Test
  void roundTripsToTheMicrosecond() {
    TimelineCursor cursor = new TimelineCursor(Instant.parse("2025-04-02T09:00:00.123456Z"), 42);
    List<ParameterError> errors = new ArrayList<>();

    assertThat(TimelineCursor.decode(cursor.encode(), errors)).isEqualTo(cursor);
    assertThat(errors).isEmpty();
  }

  @Test
  void isUrlSafe() {
    String encoded = new TimelineCursor(Instant.parse("2025-04-02T09:00:00Z"), 1234567).encode();

    assertThat(encoded).matches("[A-Za-z0-9_-]+");
  }

  @Test
  void absentMeansTheNewestPage() {
    List<ParameterError> errors = new ArrayList<>();

    assertThat(TimelineCursor.decode(null, errors)).isNull();
    assertThat(TimelineCursor.decode(" ", errors)).isNull();
    assertThat(errors).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "not a cursor",
        "!!!",
        "djF8bm90LWEtdGltZXw0Mg", // v1|not-a-time|42
        "djJ8MjAyNS0wNC0wMlQwOTowMDowMFp8NDI", // v2|2025-04-02T09:00:00Z|42
      })
  void rejectsAnythingItDidNotEncode(String value) {
    List<ParameterError> errors = new ArrayList<>();

    assertThat(TimelineCursor.decode(value, errors)).isNull();
    assertThat(errors).extracting(ParameterError::parameter).containsExactly("cursor");
  }

  @ParameterizedTest
  @ValueSource(strings = {"v1|2025-04-02T09:00:00Z|0", "v1|2025-04-02T09:00:00Z|-1", "v1|2025-04-02T09:00:00Z|x", "v1|2025-04-02T09:00:00Z"})
  void rejectsABadId(String plain) {
    String value = Base64.getUrlEncoder().withoutPadding().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    List<ParameterError> errors = new ArrayList<>();

    assertThat(TimelineCursor.decode(value, errors)).isNull();
    assertThat(errors).hasSize(1);
  }
}
