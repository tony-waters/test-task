package uk.bit1.engagement.application.patient.query;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import uk.bit1.engagement.application.common.InvalidParametersException;
import uk.bit1.engagement.application.common.ParameterError;
import uk.bit1.engagement.domain.action.Module;
import uk.bit1.engagement.domain.patient.Status;

/**
 * A validated patient-list request: free-text search, filters, sort and page. Different filters
 * combine with AND; several values within one filter combine with OR.
 *
 * @param search free text, or null for none
 * @param statuses current Statuses to include; empty means all
 * @param modules Modules the Patient has at least one Action in; empty means no filter
 * @param invitedFrom first London date of Invitation to include, or null
 * @param invitedTo last London date of Invitation to include, or null
 */
public record PatientListQuery(
    String search,
    Set<Status> statuses,
    Set<Module> modules,
    LocalDate invitedFrom,
    LocalDate invitedTo,
    PatientSort sort,
    SortDirection direction,
    int page,
    int size) {

  public static final List<Integer> PAGE_SIZES = List.of(10, 25, 50, 100);
  public static final int DEFAULT_SIZE = 25;
  public static final int MAX_SEARCH_LENGTH = 200;

  public PatientListQuery {
    statuses = statuses.isEmpty() ? EnumSet.noneOf(Status.class) : EnumSet.copyOf(statuses);
    modules = modules.isEmpty() ? EnumSet.noneOf(Module.class) : EnumSet.copyOf(modules);
  }

  /** The default list: everyone, newest Invitation first, first page of 25. */
  public static PatientListQuery defaults() {
    return new PatientListQuery(
        null, Set.of(), Set.of(), null, null, PatientSort.INVITED_AT, SortDirection.DESC, 0,
        DEFAULT_SIZE);
  }

  /**
   * Parses raw request parameters. Absent or blank values take their defaults.
   *
   * @throws InvalidParametersException listing every invalid parameter
   */
  public static PatientListQuery parse(
      String q,
      List<String> status,
      List<String> module,
      String from,
      String to,
      String sort,
      String dir,
      String page,
      String size) {
    List<ParameterError> errors = new ArrayList<>();

    String search = blank(q) ? null : q.trim();
    if (search != null && search.length() > MAX_SEARCH_LENGTH) {
      errors.add(new ParameterError("q", "must be at most " + MAX_SEARCH_LENGTH + " characters"));
    }

    Set<Status> statuses = parseEnums("status", status, Status.class, errors);
    Set<Module> modules = parseEnums("module", module, Module.class, errors);
    LocalDate invitedFrom = parseDate("from", from, errors);
    LocalDate invitedTo = parseDate("to", to, errors);
    if (invitedFrom != null && invitedTo != null && invitedFrom.isAfter(invitedTo)) {
      errors.add(new ParameterError("to", "must not be before from"));
    }

    PatientSort patientSort = PatientSort.INVITED_AT;
    if (!blank(sort)) {
      patientSort = PatientSort.fromParam(sort.trim()).orElse(null);
      if (patientSort == null) {
        errors.add(new ParameterError("sort", "must be one of " + PatientSort.allowedValues()));
      }
    }

    SortDirection direction =
        blank(sort) ? SortDirection.DESC : SortDirection.ASC; // a chosen column starts ascending
    if (!blank(dir)) {
      direction = parseEnum(dir, SortDirection.class);
      if (direction == null) {
        errors.add(new ParameterError("dir", "must be one of asc, desc"));
      }
    }

    int pageNumber = 0;
    if (!blank(page)) {
      pageNumber = parseInt(page);
      if (pageNumber < 0) {
        errors.add(new ParameterError("page", "must be a whole number, 0 or more"));
      }
    }

    int pageSize = DEFAULT_SIZE;
    if (!blank(size)) {
      pageSize = parseInt(size);
      if (!PAGE_SIZES.contains(pageSize)) {
        errors.add(new ParameterError("size", "must be one of 10, 25, 50, 100"));
      }
    }

    if (!errors.isEmpty()) {
      throw new InvalidParametersException(errors);
    }
    return new PatientListQuery(
        search, statuses, modules, invitedFrom, invitedTo, patientSort, direction, pageNumber,
        pageSize);
  }

  public PatientListQuery withPage(int newPage) {
    return new PatientListQuery(
        search, statuses, modules, invitedFrom, invitedTo, sort, direction, newPage, size);
  }

  /**
   * The same query sorted by {@code column}, back on the first page. Choosing the current column
   * flips its direction; a new column starts ascending.
   */
  public PatientListQuery sortedBy(PatientSort column) {
    SortDirection newDirection = column == sort ? direction.reversed() : SortDirection.ASC;
    return new PatientListQuery(
        search, statuses, modules, invitedFrom, invitedTo, column, newDirection, 0, size);
  }

  /** The query as URL parameters, leaving out values that equal their defaults. */
  public MultiValueMap<String, String> toParams() {
    MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
    if (search != null) {
      params.add("q", search);
    }
    statuses.forEach(s -> params.add("status", s.name()));
    modules.forEach(m -> params.add("module", m.name()));
    if (invitedFrom != null) {
      params.add("from", invitedFrom.toString());
    }
    if (invitedTo != null) {
      params.add("to", invitedTo.toString());
    }
    if (sort != PatientSort.INVITED_AT || direction != SortDirection.DESC) {
      params.add("sort", sort.param());
      params.add("dir", direction.param());
    }
    if (page != 0) {
      params.add("page", Integer.toString(page));
    }
    if (size != DEFAULT_SIZE) {
      params.add("size", Integer.toString(size));
    }
    return params;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static LocalDate parseDate(String parameter, String value, List<ParameterError> errors) {
    if (blank(value)) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      errors.add(new ParameterError(parameter, "must be a date in the form yyyy-mm-dd"));
      return null;
    }
  }

  private static <E extends Enum<E>> Set<E> parseEnums(
      String parameter, List<String> values, Class<E> type, List<ParameterError> errors) {
    Set<E> result = EnumSet.noneOf(type);
    if (values == null) {
      return result;
    }
    for (String raw : values) {
      for (String value : raw.split(",")) {
        if (value.isBlank()) {
          continue;
        }
        E parsed = parseEnum(value, type);
        if (parsed == null) {
          errors.add(new ParameterError(parameter, "must be one of " + allowed(type)));
        } else {
          result.add(parsed);
        }
      }
    }
    return result;
  }

  private static <E extends Enum<E>> E parseEnum(String value, Class<E> type) {
    try {
      return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String allowed(Class<? extends Enum<?>> type) {
    List<String> names = new ArrayList<>();
    for (Enum<?> constant : type.getEnumConstants()) {
      names.add(constant.name());
    }
    return String.join(", ", names);
  }
}
