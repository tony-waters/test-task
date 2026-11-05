package uk.bit1.engagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.bit1.engagement.web.RequestLogFilter;

/**
 * Records the caller's role for the request log. The security context is cleared before the
 * request log filter writes its line, so the role is kept as a request attribute. Runs after
 * authentication and before authorisation, so a 403 still has the role.
 */
class RoleCaptureFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    request.setAttribute(RequestLogFilter.ROLE_ATTRIBUTE, role());
    chain.doFilter(request, response);
  }

  static String role() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
      return RequestLogFilter.ANONYMOUS;
    }
    return auth.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(a -> a != null && a.startsWith("ROLE_"))
        .map(a -> a.substring("ROLE_".length()))
        .sorted()
        .findFirst()
        .orElse(RequestLogFilter.ANONYMOUS);
  }
}
