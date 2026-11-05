package uk.bit1.engagement.web;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;
import uk.bit1.engagement.London;

/** Formats values for pages, in London time. Used from templates as {@code @fmt}. */
@Component("fmt")
public class DisplayFormat {

  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(London.ZONE);
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMM yyyy").withZone(London.ZONE);

  public String dateTime(Instant instant) {
    return instant == null ? "" : DATE_TIME.format(instant);
  }

  public String date(Instant instant) {
    return instant == null ? "" : DATE.format(instant);
  }

  public String gender(String gender) {
    if (gender == null || gender.isBlank()) {
      return "Not recorded";
    }
    return gender.charAt(0) + gender.substring(1).toLowerCase();
  }

  public String age(Integer age) {
    return age == null ? "Unknown" : age.toString();
  }

  public String label(Enum<?> value) {
    String name = value.name();
    return name.charAt(0) + name.substring(1).toLowerCase();
  }
}
