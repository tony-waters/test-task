package uk.bit1.engagement.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * A full application against PostgreSQL 18 in a container. Every test class uses the same
 * properties, so they share one context and one database.
 *
 * <p>Requests run as a signed-in CLINICIAN unless a test says otherwise. Security tests override
 * this per method, or send real credentials.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=warn"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, FixedClockConfiguration.class})
@WithMockUser(username = "clinician", roles = "CLINICIAN")
public @interface IntegrationTest {}
