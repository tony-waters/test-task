package uk.bit1.engagement.patients.detail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.bit1.engagement.core.Module;
import uk.bit1.engagement.patients.detail.EngagementSummary.ModuleEngagement;
import uk.bit1.engagement.patients.detail.EngagementSummary.TopicEngagement;

class EngagementSummaryTest {

  private static Instant at(String time) {
    return Instant.parse(time);
  }

  private static EngagementRow row(
      Module module, String topic, String type, long count, String first, String last) {
    return new EngagementRow(module, topic, type, count, at(first), at(last));
  }

  @Test
  void noActionsGivesAllThreeModulesEmpty() {
    EngagementSummary summary = EngagementSummary.from(List.of());

    assertThat(summary.total()).isZero();
    assertThat(summary.firstActionAt()).isNull();
    assertThat(summary.lastActionAt()).isNull();
    assertThat(summary.modules())
        .extracting(ModuleEngagement::module)
        .containsExactly(Module.ASSESSMENT, Module.DIARY, Module.PROGRAMME);
    assertThat(summary.modules())
        .allSatisfy(
            m -> {
              assertThat(m.total()).isZero();
              assertThat(m.countsByActionType()).isEmpty();
              assertThat(m.firstActionAt()).isNull();
              assertThat(m.topics()).isEmpty();
            });
  }

  @Test
  void countsEachActionTypePerTopicAndAddsThemUpPerModule() {
    EngagementSummary summary =
        EngagementSummary.from(
            List.of(
                row(Module.PROGRAMME, "SLEEP", "STARTED", 1, "2025-02-01T09:00:00Z", "2025-02-01T09:00:00Z"),
                row(Module.PROGRAMME, "FATIGUE", "FINISHED", 1, "2025-03-01T09:00:00Z", "2025-03-01T09:00:00Z"),
                row(Module.PROGRAMME, "FATIGUE", "STARTED", 2, "2025-01-01T09:00:00Z", "2025-02-15T09:00:00Z"),
                row(Module.PROGRAMME, "FATIGUE", "ABANDONED", 1, "2025-01-10T09:00:00Z", "2025-01-10T09:00:00Z"),
                row(Module.PROGRAMME, "FATIGUE", "TASK-COMPLETED", 5, "2025-01-02T09:00:00Z", "2025-02-20T09:00:00Z")));

    ModuleEngagement programme = summary.modules().get(2);
    assertThat(programme.module()).isEqualTo(Module.PROGRAMME);
    assertThat(programme.total()).isEqualTo(10);
    assertThat(programme.firstActionAt()).isEqualTo(at("2025-01-01T09:00:00Z"));
    assertThat(programme.lastActionAt()).isEqualTo(at("2025-03-01T09:00:00Z"));
    assertThat(programme.countsByActionType())
        .containsExactly(
            Map.entry("STARTED", 3L),
            Map.entry("TASK-COMPLETED", 5L),
            Map.entry("FINISHED", 1L),
            Map.entry("ABANDONED", 1L));

    assertThat(programme.topics()).extracting(TopicEngagement::topic).containsExactly("FATIGUE", "SLEEP");
    TopicEngagement fatigue = programme.topics().getFirst();
    assertThat(fatigue.total()).isEqualTo(9);
    assertThat(fatigue.countsByActionType()).containsEntry("STARTED", 2L).doesNotContainKey("COMPLETED");
    assertThat(fatigue.firstActionAt()).isEqualTo(at("2025-01-01T09:00:00Z"));
    assertThat(fatigue.lastActionAt()).isEqualTo(at("2025-03-01T09:00:00Z"));
  }

  @Test
  void spansEveryModuleForTheOverallFirstAndLast() {
    EngagementSummary summary =
        EngagementSummary.from(
            List.of(
                row(Module.DIARY, "BLADDER", "STARTED", 1, "2025-05-01T09:00:00Z", "2025-05-01T09:00:00Z"),
                row(Module.ASSESSMENT, "PHQ9", "COMPLETED", 3, "2025-01-01T09:00:00Z", "2025-04-01T09:00:00Z")));

    assertThat(summary.total()).isEqualTo(4);
    assertThat(summary.firstActionAt()).isEqualTo(at("2025-01-01T09:00:00Z"));
    assertThat(summary.lastActionAt()).isEqualTo(at("2025-05-01T09:00:00Z"));
    assertThat(summary.modules().get(0).countsByActionType()).containsExactly(Map.entry("COMPLETED", 3L));
    assertThat(summary.modules().get(2).total()).isZero();
  }

  @Test
  void anUnfamiliarActionTypeIsCountedAfterTheKnownOnes() {
    EngagementSummary summary =
        EngagementSummary.from(
            List.of(
                row(Module.DIARY, "BLADDER", "PAUSED", 1, "2025-05-01T09:00:00Z", "2025-05-01T09:00:00Z"),
                row(Module.DIARY, "BLADDER", "STARTED", 1, "2025-04-01T09:00:00Z", "2025-04-01T09:00:00Z")));

    assertThat(summary.modules().get(1).countsByActionType().keySet()).containsExactly("STARTED", "PAUSED");
    assertThat(summary.modules().get(1).total()).isEqualTo(2);
  }

  @Test
  void columnsAreTheKnownTypesThenAnyOthersThePatientHas() {
    EngagementSummary summary =
        EngagementSummary.from(
            List.of(
                row(Module.DIARY, "BLADDER", "PAUSED", 1, "2025-05-01T09:00:00Z", "2025-05-01T09:00:00Z"),
                row(Module.PROGRAMME, "FATIGUE", "ARCHIVED", 1, "2025-05-01T09:00:00Z", "2025-05-01T09:00:00Z")));

    assertThat(summary.columnActionTypes())
        .containsExactly(
            "STARTED", "TASK-COMPLETED", "TASK-SKIPPED", "FINISHED", "ABANDONED", "COMPLETED",
            "ARCHIVED", "PAUSED");
  }

  @Test
  void withNoUnfamiliarTypesTheColumnsAreTheKnownSix() {
    assertThat(EngagementSummary.from(List.of()).columnActionTypes())
        .containsExactlyElementsOf(EngagementSummary.ACTION_TYPES);
  }
}
