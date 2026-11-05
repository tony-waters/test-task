package uk.bit1.engagement.action;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A Patient's counts of Actions by Action type, for each Module and each Topic within it, with
 * the first and last time they recorded one. Every Action counts, including post-discharge
 * Actions and suspect timestamps. Start-to-finish runs are not reconstructed.
 *
 * @param total every Action the Patient recorded
 * @param modules all three Modules in their usual order, including those with no Actions
 */
public record EngagementSummary(
    long total, Instant firstActionAt, Instant lastActionAt, List<ModuleEngagement> modules) {

  /** The Action types in journey order. Counts are listed in this order, then any others. */
  public static final List<String> ACTION_TYPES =
      List.of("STARTED", "TASK-COMPLETED", "TASK-SKIPPED", "FINISHED", "ABANDONED", "COMPLETED");

  private static final Comparator<String> ACTION_TYPE_ORDER =
      Comparator.<String>comparingInt(
              t -> ACTION_TYPES.contains(t) ? ACTION_TYPES.indexOf(t) : ACTION_TYPES.size())
          .thenComparing(Comparator.naturalOrder());

  /**
   * @param countsByActionType Action type to count, in journey order; only types that occur
   * @param topics Topics with at least one Action, alphabetically
   */
  public record ModuleEngagement(
      Module module,
      long total,
      Map<String, Long> countsByActionType,
      Instant firstActionAt,
      Instant lastActionAt,
      List<TopicEngagement> topics) {}

  /** @param countsByActionType Action type to count, in journey order; only types that occur */
  public record TopicEngagement(
      String topic,
      long total,
      Map<String, Long> countsByActionType,
      Instant firstActionAt,
      Instant lastActionAt) {}

  /**
   * The Action types to show as columns: the known ones always, then any others this Patient has,
   * so every count has a column and the columns add up to the total.
   */
  public List<String> columnActionTypes() {
    Set<String> types = new TreeSet<>(ACTION_TYPE_ORDER);
    types.addAll(ACTION_TYPES);
    modules.forEach(m -> types.addAll(m.countsByActionType().keySet()));
    return List.copyOf(types);
  }

  /** Builds the summary from per-(Module, Topic, Action type) rows, in any order. */
  public static EngagementSummary from(Collection<EngagementRow> rows) {
    List<ModuleEngagement> modules = new ArrayList<>();
    for (Module module : Module.values()) {
      Map<String, List<EngagementRow>> byTopic = new TreeMap<>();
      rows.stream()
          .filter(r -> r.module() == module)
          .forEach(r -> byTopic.computeIfAbsent(r.topic(), t -> new ArrayList<>()).add(r));
      List<TopicEngagement> topics = new ArrayList<>();
      byTopic.forEach(
          (topic, topicRows) ->
              topics.add(
                  new TopicEngagement(
                      topic, total(topicRows), counts(topicRows), first(topicRows),
                      last(topicRows))));
      List<EngagementRow> moduleRows = byTopic.values().stream().flatMap(List::stream).toList();
      modules.add(
          new ModuleEngagement(
              module, total(moduleRows), counts(moduleRows), first(moduleRows), last(moduleRows),
              List.copyOf(topics)));
    }
    return new EngagementSummary(total(rows), first(rows), last(rows), List.copyOf(modules));
  }

  private static long total(Collection<EngagementRow> rows) {
    return rows.stream().mapToLong(EngagementRow::count).sum();
  }

  private static Map<String, Long> counts(Collection<EngagementRow> rows) {
    Map<String, Long> sorted = new TreeMap<>(ACTION_TYPE_ORDER);
    rows.forEach(r -> sorted.merge(r.actionType(), r.count(), Long::sum));
    return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
  }

  private static Instant first(Collection<EngagementRow> rows) {
    return rows.stream().map(EngagementRow::first).min(Comparator.naturalOrder()).orElse(null);
  }

  private static Instant last(Collection<EngagementRow> rows) {
    return rows.stream().map(EngagementRow::last).max(Comparator.naturalOrder()).orElse(null);
  }
}
