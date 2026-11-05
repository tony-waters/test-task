package uk.bit1.engagement.patient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import uk.bit1.engagement.action.Module;
import uk.bit1.engagement.web.InvalidParametersException;
import uk.bit1.engagement.web.ParameterError;

class PatientListQueryTest {

  @Test
  void noParametersGiveTheDefaults() {
    PatientListQuery query = parse(null, null, null, null, null, null, null, null, null);

    assertThat(query).isEqualTo(PatientListQuery.defaults());
    assertThat(query.sort()).isEqualTo(PatientSort.INVITED_AT);
    assertThat(query.direction()).isEqualTo(SortDirection.DESC);
    assertThat(query.size()).isEqualTo(25);
    assertThat(query.toParams()).isEmpty();
  }

  @Test
  void parsesEveryParameter() {
    PatientListQuery query =
        parse(
            "  ada ", List.of("invited", "DISCHARGED"), List.of("diary,programme"), "2025-01-01",
            "2025-01-31", "age", "desc", "2", "50");

    assertThat(query.search()).isEqualTo("ada");
    assertThat(query.statuses()).containsExactlyInAnyOrder(Status.INVITED, Status.DISCHARGED);
    assertThat(query.modules()).containsExactlyInAnyOrder(Module.DIARY, Module.PROGRAMME);
    assertThat(query.invitedFrom()).isEqualTo(LocalDate.of(2025, 1, 1));
    assertThat(query.invitedTo()).isEqualTo(LocalDate.of(2025, 1, 31));
    assertThat(query.sort()).isEqualTo(PatientSort.AGE);
    assertThat(query.direction()).isEqualTo(SortDirection.DESC);
    assertThat(query.page()).isEqualTo(2);
    assertThat(query.size()).isEqualTo(50);
  }

  @Test
  void aChosenSortStartsAscending() {
    assertThat(parse(null, null, null, null, null, "familyName", null, null, null).direction())
        .isEqualTo(SortDirection.ASC);
  }

  @Test
  void reportsEveryInvalidParameter() {
    InvalidParametersException e =
        catchThrowableOfType(
            InvalidParametersException.class,
            () ->
                parse(
                    null, List.of("ACTIVE"), List.of("SURGERY"), "01/02/2025", "yesterday",
                    "nhsNumber", "up", "-1", "20"));

    assertThat(e.getErrors())
        .extracting(ParameterError::parameter)
        .containsExactlyInAnyOrder("status", "module", "from", "to", "sort", "dir", "page", "size");
  }

  @Test
  void rejectsARangeThatEndsBeforeItStarts() {
    InvalidParametersException e =
        catchThrowableOfType(
            InvalidParametersException.class,
            () -> parse(null, null, null, "2025-02-01", "2025-01-01", null, null, null, null));

    assertThat(e.getErrors()).containsExactly(new ParameterError("to", "must not be before from"));
  }

  @Test
  void rejectsANonNumericPage() {
    InvalidParametersException e =
        catchThrowableOfType(
            InvalidParametersException.class,
            () -> parse(null, null, null, null, null, null, null, "two", null));

    assertThat(e.getErrors()).extracting(ParameterError::parameter).containsExactly("page");
  }

  @Test
  void paramsRoundTrip() {
    PatientListQuery query =
        parse(
            "smith", List.of("REGISTERED"), List.of("ASSESSMENT"), "2025-01-01", null, "status",
            "asc", "1", "10");

    var p = query.toParams();
    PatientListQuery again =
        parse(
            p.getFirst("q"), p.get("status"), p.get("module"), p.getFirst("from"),
            p.getFirst("to"), p.getFirst("sort"), p.getFirst("dir"), p.getFirst("page"),
            p.getFirst("size"));

    assertThat(again).isEqualTo(query);
  }

  @Test
  void sortingByTheCurrentColumnFlipsItAndReturnsToTheFirstPage() {
    PatientListQuery query = PatientListQuery.defaults().withPage(3);

    PatientListQuery flipped = query.sortedBy(PatientSort.INVITED_AT);
    PatientListQuery other = query.sortedBy(PatientSort.FAMILY_NAME);

    assertThat(flipped.direction()).isEqualTo(SortDirection.ASC);
    assertThat(flipped.page()).isZero();
    assertThat(other.sort()).isEqualTo(PatientSort.FAMILY_NAME);
    assertThat(other.direction()).isEqualTo(SortDirection.ASC);
  }

  private static PatientListQuery parse(
      String q, List<String> status, List<String> module, String from, String to, String sort,
      String dir, String page, String size) {
    return PatientListQuery.parse(q, status, module, from, to, sort, dir, page, size);
  }
}
