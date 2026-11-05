package uk.bit1.engagement.dashboard;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * How Patients move through the journey, by strict progression: each stage counts only Patients
 * who also reached the stage before it. A skipped registration (discharged without registering)
 * is therefore Invited but not Discharged here, and is reported in {@code skippedRegistration}
 * for the footnote.
 *
 * @param invitedToRegistered registered / invited, or null when no one was invited
 * @param registeredToDischarged discharged / registered, or null when no one registered
 * @param skippedRegistration Patients left out of the Discharged stage
 */
public record Funnel(
    long invited,
    long registered,
    long discharged,
    Double invitedToRegistered,
    Double registeredToDischarged,
    long skippedRegistration,
    MedianTime invitedToRegisteredTime,
    MedianTime registeredToDischargedTime) {

  /**
   * The median time a transition took, over only the Patients who completed it. Those still
   * waiting are left out, so it understates the real wait; {@code n} is shown alongside.
   *
   * @param seconds the median in whole seconds, or null when {@code n} is 0
   */
  public record MedianTime(Long seconds, int n) {

    static MedianTime of(List<Duration> durations) {
      if (durations.isEmpty()) {
        return new MedianTime(null, 0);
      }
      List<Duration> sorted = durations.stream().sorted().toList();
      int mid = sorted.size() / 2;
      Duration median =
          sorted.size() % 2 == 1
              ? sorted.get(mid)
              : sorted.get(mid - 1).plus(sorted.get(mid)).dividedBy(2);
      return new MedianTime(median.toSeconds(), sorted.size());
    }
  }

  public static Funnel from(Collection<Journey> journeys) {
    long registered = 0;
    long discharged = 0;
    long skipped = 0;
    List<Duration> toRegister = new ArrayList<>();
    List<Duration> toDischarge = new ArrayList<>();
    for (Journey j : journeys) {
      if (j.skippedRegistration()) {
        skipped++;
      }
      if (j.registeredAt() == null) {
        continue;
      }
      registered++;
      toRegister.add(Duration.between(j.invitedAt(), j.registeredAt()));
      if (j.dischargedAt() != null) {
        discharged++;
        toDischarge.add(Duration.between(j.registeredAt(), j.dischargedAt()));
      }
    }
    long invited = journeys.size();
    return new Funnel(
        invited,
        registered,
        discharged,
        rate(registered, invited),
        rate(discharged, registered),
        skipped,
        MedianTime.of(toRegister),
        MedianTime.of(toDischarge));
  }

  private static Double rate(long part, long whole) {
    return whole == 0 ? null : (double) part / whole;
  }
}
