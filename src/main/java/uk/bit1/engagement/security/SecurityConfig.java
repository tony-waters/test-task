package uk.bit1.engagement.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Two roles. CLINICIAN reads everything; ADMIN reads everything and can also export. The API and
 * Actuator use HTTP Basic and answer 401 and 403 as ProblemDetail; the pages use form login.
 *
 * <p>The API chain also accepts the session from a form login, so the JSON links on the pages and
 * Swagger UI work in the browser. Basic requests don't create a session.
 *
 * <p>Web only: load mode runs the same jar without a web server, and has nothing to secure.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

  public static final String CLINICIAN = "CLINICIAN";
  public static final String ADMIN = "ADMIN";

  @Bean
  @Order(1)
  SecurityFilterChain api(HttpSecurity http, ProblemResponses problems) throws Exception {
    http.securityMatcher("/api/**", "/actuator/**")
        .authorizeHttpRequests(
            a ->
                a.requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/**", "/api/patients/export.csv")
                    .hasRole(ADMIN)
                    .anyRequest()
                    .hasAnyRole(CLINICIAN, ADMIN))
        .httpBasic(b -> b.authenticationEntryPoint(problems))
        .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .addFilterAfter(new RoleCaptureFilter(), AnonymousAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  SecurityFilterChain pages(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            a ->
                a.requestMatchers("/app.css", "/error")
                    .permitAll()
                    .anyRequest()
                    .hasAnyRole(CLINICIAN, ADMIN))
        .formLogin(f -> f.defaultSuccessUrl("/patients"))
        .logout(l -> l.logoutSuccessUrl("/login?logout"))
        .addFilterAfter(new RoleCaptureFilter(), AnonymousAuthenticationFilter.class);
    return http.build();
  }

  /** The two users. Passwords come from the environment, with local defaults (see compose.yaml). */
  @Bean
  UserDetailsService users(
      @Value("${app.security.clinician-password}") String clinicianPassword,
      @Value("${app.security.admin-password}") String adminPassword) {
    PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    return new InMemoryUserDetailsManager(
        User.withUsername("clinician")
            .password(encoder.encode(clinicianPassword))
            .roles(CLINICIAN)
            .build(),
        User.withUsername("admin").password(encoder.encode(adminPassword)).roles(ADMIN).build());
  }
}
