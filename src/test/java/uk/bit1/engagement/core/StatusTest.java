package uk.bit1.engagement.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StatusTest {

  private static final Instant REGISTERED = Instant.parse("2025-03-05T09:00:00Z");
  private static final Instant DISCHARGED = Instant.parse("2025-05-01T10:00:00Z");

  @Test
  void invitedOnlyIsInvited() {
    assertThat(Status.derive(null, null)).isEqualTo(Status.INVITED);
  }

  @Test
  void registeredIsRegistered() {
    assertThat(Status.derive(REGISTERED, null)).isEqualTo(Status.REGISTERED);
  }

  @Test
  void registeredAndDischargedIsDischarged() {
    assertThat(Status.derive(REGISTERED, DISCHARGED)).isEqualTo(Status.DISCHARGED);
  }

  @Test
  void skippedRegistrationIsStillDischarged() {
    assertThat(Status.derive(null, DISCHARGED)).isEqualTo(Status.DISCHARGED);
  }
}
