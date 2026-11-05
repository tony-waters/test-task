package uk.bit1.engagement.dashboard;

import java.util.Collection;
import uk.bit1.engagement.patient.Status;

/**
 * How many Patients are at each current Status. The buckets are mutually exclusive and add up to
 * the total: a skipped registration counts as Discharged.
 */
public record StatusCounts(long invited, long registered, long discharged, long total) {

  public static StatusCounts from(Collection<Journey> journeys) {
    long invited = 0;
    long registered = 0;
    long discharged = 0;
    for (Journey j : journeys) {
      switch (Status.derive(j.registeredAt(), j.dischargedAt())) {
        case INVITED -> invited++;
        case REGISTERED -> registered++;
        case DISCHARGED -> discharged++;
      }
    }
    return new StatusCounts(invited, registered, discharged, journeys.size());
  }
}
