package uk.bit1.engagement.patients.list;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.hibernate.jpa.HibernateHints;
import uk.bit1.engagement.core.Age;
import uk.bit1.engagement.core.Status;
import uk.bit1.engagement.shared.London;

/**
 * Searches, filters, sorts and pages the patient list in the database. A page costs two
 * statements, the rows and the count, whatever its size.
 *
 * <p>Dynamic JPQL rather than Specifications: the projection carries a correlated subquery for the
 * last Action time, which the sort can use, and the statement count stays fixed.
 */
class PatientSearchImpl implements PatientSearch {

  /** The JPQL projection: one row per Patient, with the time of their latest Action. */
  private static final String SELECT =
      """
      select p.uuid, p.title, p.givenName, p.familyName, p.gender, p.dateOfBirth, p.nhsNumber,
             p.hospitalId, p.status, p.invitedAt, p.registeredAt, p.dischargedAt,
             (select max(a.recordedAt) from Action a where a.patient = p) as lastActionAt
        from Patient p
      """;

  private final EntityManager em;
  private final Clock clock;

  PatientSearchImpl(EntityManager em, Clock clock) {
    this.em = em;
    this.clock = clock;
  }

  @Override
  public PatientPage search(PatientListQuery query) {
    Map<String, Object> params = new HashMap<>();
    String where = where(query, params);

    TypedQuery<Long> count =
        em.createQuery("select count(p) from Patient p" + where, Long.class);
    params.forEach(count::setParameter);
    long total = count.getSingleResult();

    // Computed as a long: page * size can overflow an int for a large accepted page number.
    long offset = (long) query.page() * query.size();
    List<PatientSummary> content = List.of();
    if (offset < total) {
      TypedQuery<Object[]> rows = rowsQuery(query, where, params);
      rows.setFirstResult((int) offset); // offset < total, and a count fits an int here
      rows.setMaxResults(query.size());
      LocalDate today = London.today(clock);
      content = rows.getResultList().stream().map(r -> toSummary(r, today)).toList();
    }
    int totalPages = (int) ((total + query.size() - 1) / query.size());
    return new PatientPage(
        content,
        query.page(),
        query.size(),
        total,
        totalPages,
        query.sort().param(),
        query.direction().param());
  }

  /**
   * Every matching row, in list order, read through a database cursor {@code fetchSize} rows at a
   * time. The caller must close the stream, inside a transaction: PostgreSQL only uses a cursor
   * when auto-commit is off.
   */
  @Override
  public Stream<PatientSummary> stream(PatientListQuery query, int fetchSize) {
    Map<String, Object> params = new HashMap<>();
    TypedQuery<Object[]> rows = rowsQuery(query, where(query, params), params);
    rows.setHint(HibernateHints.HINT_FETCH_SIZE, fetchSize);
    rows.setHint(HibernateHints.HINT_READ_ONLY, true);
    LocalDate today = London.today(clock);
    return rows.getResultStream().map(r -> toSummary(r, today));
  }

  private TypedQuery<Object[]> rowsQuery(
      PatientListQuery query, String where, Map<String, Object> params) {
    String orderBy = " order by " + query.sort().orderBy(query.direction()) + ", p.id asc";
    TypedQuery<Object[]> rows = em.createQuery(SELECT + where + orderBy, Object[].class);
    params.forEach(rows::setParameter);
    return rows;
  }

  private static String where(PatientListQuery query, Map<String, Object> params) {
    List<String> clauses = new ArrayList<>();

    if (query.search() != null) {
      List<String> matches = new ArrayList<>();
      matches.add("lower(p.givenName) like :text escape '\\'");
      matches.add("lower(p.familyName) like :text escape '\\'");
      matches.add("lower(p.hospitalId) like :text escape '\\'");
      params.put("text", "%" + escapeLike(query.search().toLowerCase(Locale.ROOT)) + "%");
      String nhs = query.search().replaceAll("\\s", "");
      if (!nhs.isEmpty()) {
        matches.add("replace(p.nhsNumber, ' ', '') = :nhs");
        params.put("nhs", nhs);
      }
      clauses.add("(" + String.join(" or ", matches) + ")");
    }
    if (!query.statuses().isEmpty()) {
      clauses.add("p.status in :statuses");
      params.put("statuses", query.statuses());
    }
    if (!query.modules().isEmpty()) {
      clauses.add(
          "exists (select 1 from Action m where m.patient = p and m.module in :modules)");
      params.put("modules", query.modules());
    }
    if (query.invitedFrom() != null) {
      clauses.add("p.invitedAt >= :invitedFrom");
      params.put("invitedFrom", London.startOf(query.invitedFrom()));
    }
    if (query.invitedTo() != null) {
      clauses.add("p.invitedAt < :invitedBefore");
      params.put("invitedBefore", London.startOf(query.invitedTo().plusDays(1)));
    }
    return clauses.isEmpty() ? "" : " where " + String.join(" and ", clauses);
  }

  /** Makes LIKE wildcards in user input match literally. */
  static String escapeLike(String text) {
    return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static PatientSummary toSummary(Object[] r, LocalDate today) {
    LocalDate dateOfBirth = (LocalDate) r[5];
    return new PatientSummary(
        (UUID) r[0],
        (String) r[1],
        (String) r[2],
        (String) r[3],
        (String) r[4],
        dateOfBirth,
        Age.inYears(dateOfBirth, today),
        (String) r[6],
        (String) r[7],
        (Status) r[8],
        (Instant) r[9],
        (Instant) r[10],
        (Instant) r[11],
        (Instant) r[12]);
  }
}
