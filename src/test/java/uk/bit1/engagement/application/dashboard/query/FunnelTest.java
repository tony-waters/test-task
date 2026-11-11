package uk.bit1.engagement.application.dashboard.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class FunnelTest {

  private static final Instant T0 = Instant.parse("2025-01-06T10:00:00Z");

  private static Journey invited() {
    return new Journey(T0, null, null);
  }

  private static Journey registered(Duration after) {
    return new Journey(T0, T0.plus(after), null);
  }

  private static Journey discharged(Duration toRegister, Duration toDischarge) {
    Instant registeredAt = T0.plus(toRegister);
    return new Journey(T0, registeredAt, registeredAt.plus(toDischarge));
  }

  private static Journey skippedRegistration() {
    return new Journey(T0, null, T0.plus(Duration.ofDays(30)));
  }

  @Test
  void skippedRegistrationIsInvitedButNotDischargedAndIsCountedForTheFootnote() {
    Funnel funnel =
        Funnel.from(
            List.of(
                invited(),
                registered(Duration.ofDays(1)),
                discharged(Duration.ofDays(1), Duration.ofDays(10)),
                skippedRegistration()));

    assertThat(funnel.invited()).isEqualTo(4);
    assertThat(funnel.registered()).isEqualTo(2);
    assertThat(funnel.discharged()).isEqualTo(1);
    assertThat(funnel.skippedRegistration()).isEqualTo(1);
    assertThat(funnel.invitedToRegistered()).isEqualTo(0.5);
    assertThat(funnel.registeredToDischarged()).isEqualTo(0.5);
  }

  @Test
  void skippedRegistrationIsLeftOutOfBothMedians() {
    Funnel funnel =
        Funnel.from(List.of(registered(Duration.ofHours(5)), skippedRegistration()));

    assertThat(funnel.invitedToRegisteredTime().n()).isEqualTo(1);
    assertThat(funnel.invitedToRegisteredTime().seconds()).isEqualTo(5 * 3600);
    assertThat(funnel.registeredToDischargedTime().n()).isZero();
    assertThat(funnel.registeredToDischargedTime().seconds()).isNull();
  }

  @Test
  void mediansCoverOnlyPatientsWhoCompletedTheTransition() {
    Funnel funnel =
        Funnel.from(
            List.of(
                invited(),
                invited(),
                registered(Duration.ofHours(1)),
                registered(Duration.ofHours(3)),
                discharged(Duration.ofHours(100), Duration.ofDays(7))));

    // Odd count: the middle one of 1 h, 3 h, 100 h.
    assertThat(funnel.invitedToRegisteredTime().seconds()).isEqualTo(3 * 3600);
    assertThat(funnel.invitedToRegisteredTime().n()).isEqualTo(3);
    assertThat(funnel.registeredToDischargedTime().seconds()).isEqualTo(7 * 86400);
    assertThat(funnel.registeredToDischargedTime().n()).isEqualTo(1);
  }

  @Test
  void anEvenCountTakesTheMeanOfTheMiddleTwo() {
    Funnel funnel =
        Funnel.from(
            List.of(
                registered(Duration.ofHours(4)),
                registered(Duration.ofHours(1)),
                registered(Duration.ofHours(2)),
                registered(Duration.ofHours(100))));

    assertThat(funnel.invitedToRegisteredTime().seconds()).isEqualTo(3 * 3600);
    assertThat(funnel.invitedToRegisteredTime().n()).isEqualTo(4);
  }

  @Test
  void conversionRatesAreFractionsOfThePreviousStage() {
    Funnel funnel =
        Funnel.from(
            List.of(
                invited(),
                registered(Duration.ofDays(1)),
                registered(Duration.ofDays(1)),
                discharged(Duration.ofDays(1), Duration.ofDays(1))));

    assertThat(funnel.invitedToRegistered()).isEqualTo(0.75);
    assertThat(funnel.registeredToDischarged()).isCloseTo(1.0 / 3, within(1e-9));
  }

  @Test
  void anEmptyPopulationHasNoRatesOrMedians() {
    Funnel funnel = Funnel.from(List.of());

    assertThat(funnel.invited()).isZero();
    assertThat(funnel.registered()).isZero();
    assertThat(funnel.discharged()).isZero();
    assertThat(funnel.skippedRegistration()).isZero();
    assertThat(funnel.invitedToRegistered()).isNull();
    assertThat(funnel.registeredToDischarged()).isNull();
    assertThat(funnel.invitedToRegisteredTime()).isEqualTo(new Funnel.MedianTime(null, 0));
    assertThat(funnel.registeredToDischargedTime()).isEqualTo(new Funnel.MedianTime(null, 0));
  }

  @Test
  void noOneRegisteredGivesAZeroRateThenNone() {
    Funnel funnel = Funnel.from(List.of(invited(), invited()));

    assertThat(funnel.invitedToRegistered()).isEqualTo(0.0);
    assertThat(funnel.registeredToDischarged()).isNull();
  }
}
