package uk.bit1.engagement.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Writes one structured log line per request: request ID (in the MDC, so other lines from the same
 * request carry it too), role, endpoint, status and duration.
 *
 * <p>No patient identifiers. The endpoint is the route pattern, such as {@code
 * /api/patients/{uuid}}, with the Patient's UUID in its own field. The query string is never
 * logged: a search term appears only as its length.
 *
 * <p>Runs outside the security filters, so rejected requests are logged too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLogFilter extends OncePerRequestFilter {

  public static final String ROLE_ATTRIBUTE = RequestLogFilter.class.getName() + ".role";
  public static final String ANONYMOUS = "anonymous";
  public static final String REQUEST_ID = "requestId";
  static final String REQUEST_ID_HEADER = "X-Request-Id";

  private static final Logger log = LoggerFactory.getLogger(RequestLogFilter.class);
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Pattern UUID_SEGMENT =
      Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
  private static final Pattern LITERAL_SEGMENT = Pattern.compile("[a-z][a-z.-]{0,39}");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long start = System.nanoTime();
    String requestId = requestId(request);
    response.setHeader(REQUEST_ID_HEADER, requestId);
    MDC.put(REQUEST_ID, requestId);
    int status = 500;
    try {
      chain.doFilter(request, response);
      status = response.getStatus();
    } finally {
      try {
        write(request, status, (System.nanoTime() - start) / 1_000_000);
      } finally {
        MDC.remove(REQUEST_ID);
      }
    }
  }

  private static void write(HttpServletRequest request, int status, long durationMs) {
    Object role = request.getAttribute(ROLE_ATTRIBUTE);
    LoggingEventBuilder line =
        log.atInfo()
            .setMessage("request")
            .addKeyValue("role", role == null ? ANONYMOUS : role)
            .addKeyValue("method", request.getMethod())
            .addKeyValue("endpoint", endpoint(request))
            .addKeyValue("status", status)
            .addKeyValue("durationMs", durationMs);
    Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    if (variables instanceof Map<?, ?> map
        && map.get("uuid") instanceof String uuid
        && UUID_SEGMENT.matcher(uuid).matches()) {
      line = line.addKeyValue("patient", uuid);
    }
    String q = request.getParameter("q");
    if (q != null) {
      line = line.addKeyValue("searchLength", q.trim().length());
    }
    line.log();
  }

  /**
   * The route pattern when Spring MVC matched one. Otherwise (static files, unknown paths, requests
   * rejected before MVC) the path, with any segment that isn't a UUID or a plain lower-case word
   * replaced, because a mistyped path could contain anything.
   */
  static String endpoint(HttpServletRequest request) {
    Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    if (pattern instanceof String p && !p.contains("**")) {
      return p;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (path.length() > 200) {
      return "{long path}";
    }
    StringBuilder safe = new StringBuilder();
    for (String segment : path.split("/", -1)) {
      if (!safe.isEmpty() || !segment.isEmpty()) {
        safe.append('/');
      }
      boolean keep =
          segment.isEmpty()
              || UUID_SEGMENT.matcher(segment).matches()
              || LITERAL_SEGMENT.matcher(segment).matches();
      safe.append(keep ? segment : "{redacted}");
    }
    return safe.isEmpty() ? "/" : safe.toString();
  }

  /** The caller's {@code X-Request-Id} when it looks safe to log, else a new one. */
  private static String requestId(HttpServletRequest request) {
    String given = request.getHeader(REQUEST_ID_HEADER);
    if (given != null && SAFE_REQUEST_ID.matcher(given).matches()) {
      return given;
    }
    return UUID.randomUUID().toString();
  }
}
