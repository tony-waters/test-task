package uk.bit1.engagement.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Pins "today" so ages are stable: 2026-11-05 in London. */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

  public static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

  @Bean
  @Primary
  Clock fixedClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
