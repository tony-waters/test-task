package uk.bit1.engagement.web;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Tells every page who is signed in, for the header's sign-out form, and whether they may export.
 * The pages only hide the export link; the API's security rules are what enforce it.
 */
@ControllerAdvice
class CurrentUserAdvice {

  @ModelAttribute
  void addUser(Model model) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || auth instanceof AnonymousAuthenticationToken || !auth.isAuthenticated()) {
      return;
    }
    model.addAttribute("currentUser", auth.getName());
    model.addAttribute(
        "canExport",
        auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
  }
}
