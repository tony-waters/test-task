package uk.bit1.engagement.patients.list;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.bit1.engagement.core.Module;
import uk.bit1.engagement.core.Status;
import uk.bit1.engagement.importer.DumpLoader;
import uk.bit1.engagement.support.IntegrationTest;
import uk.bit1.engagement.support.StandardData;

/** The N+1 guard: a page of the list costs the same statements whatever its size. */
@IntegrationTest
class PatientListStatementCountTest {

  @Autowired PatientSearchRepository patients;
  @Autowired EntityManagerFactory emf;
  @Autowired DumpLoader loader;

  @BeforeEach
  void load() {
    loader.load(StandardData.file());
  }

  @Test
  void statementCountDoesNotGrowWithPageSize() {
    long atTen = statementsFor(withSize(10));
    long atHundred = statementsFor(withSize(100));

    assertThat(atTen).isEqualTo(atHundred).isEqualTo(2);
  }

  @Test
  void holdsWithEveryFilterAndTheLastActionSort() {
    PatientListQuery ten =
        new PatientListQuery(
            "e", Set.of(Status.REGISTERED), Set.of(Module.ASSESSMENT), null, null,
            PatientSort.LAST_ACTION_AT, SortDirection.DESC, 0, 10);
    PatientListQuery hundred =
        new PatientListQuery(
            "e", Set.of(Status.REGISTERED), Set.of(Module.ASSESSMENT), null, null,
            PatientSort.LAST_ACTION_AT, SortDirection.DESC, 0, 100);

    assertThat(patients.search(ten).content()).hasSize(10);
    assertThat(statementsFor(ten)).isEqualTo(statementsFor(hundred));
  }

  private static PatientListQuery withSize(int size) {
    PatientListQuery d = PatientListQuery.defaults();
    return new PatientListQuery(
        d.search(), d.statuses(), d.modules(), d.invitedFrom(), d.invitedTo(), d.sort(),
        d.direction(), 0, size);
  }

  private long statementsFor(PatientListQuery query) {
    Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
    stats.clear();
    PatientPage page = patients.search(query);
    assertThat(page.content()).isNotEmpty();
    return stats.getPrepareStatementCount();
  }
}
