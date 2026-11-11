package uk.bit1.engagement.application.common;

import java.time.ZoneId;
import uk.bit1.engagement.domain.common.London;

/** The zone the web layer shows times in: London, as everywhere else (ADR 0001). */
public final class DisplayZone {

  public static final ZoneId ZONE = London.ZONE;

  private DisplayZone() {}
}
