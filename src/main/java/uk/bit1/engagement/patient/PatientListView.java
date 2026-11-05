package uk.bit1.engagement.patient;

import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;
import java.util.stream.IntStream;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriUtils;

/** What the list page needs: the query, its results, and links that keep the URL state. */
public record PatientListView(PatientListQuery query, PatientPage result) {

  private static final String PATH = "/patients";
  private static final String EXPORT_PATH = "/api/patients/export.csv";

  public String sortUrl(String column) {
    PatientSort sort = PatientSort.fromParam(column).orElseThrow();
    return url(query.sortedBy(sort));
  }

  /** "asc" or "desc" when the list is sorted by this column, else empty. */
  public String sortState(String column) {
    return query.sort().param().equals(column) ? query.direction().param() : "";
  }

  public String pageUrl(int page) {
    return url(query.withPage(page));
  }

  public boolean hasPrevious() {
    return query.page() > 0;
  }

  public boolean hasNext() {
    return query.page() + 1 < result.totalPages();
  }

  public int[] pageNumbers() {
    return IntStream.range(0, result.totalPages()).toArray();
  }

  public String currentUrl() {
    return url(query);
  }

  /** The CSV export of this list: same search, filters and sort, without paging. */
  public String exportUrl() {
    MultiValueMap<String, String> params = query.toParams();
    params.remove("page");
    params.remove("size");
    return url(EXPORT_PATH, params);
  }

  private static String url(PatientListQuery q) {
    return url(PATH, q.toParams());
  }

  /**
   * Encodes every value strictly. Query-component encoding would leave a literal {@code +} as is,
   * and the next request would decode it as a space.
   */
  private static String url(String path, MultiValueMap<String, String> params) {
    StringJoiner query = new StringJoiner("&");
    params
        .forEach(
            (name, values) ->
                values.forEach(
                    v ->
                        query.add(
                            UriUtils.encode(name, StandardCharsets.UTF_8)
                                + "="
                                + UriUtils.encode(v, StandardCharsets.UTF_8))));
    return query.length() == 0 ? path : path + "?" + query;
  }
}
