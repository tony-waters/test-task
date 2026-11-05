package uk.bit1.engagement.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import uk.bit1.engagement.support.StandardData;
import uk.bit1.engagement.support.TestcontainersConfiguration;

/**
 * Load mode as Docker Compose runs it: the same jar, no web server, {@code app.load.file} set.
 * Every other integration test starts the web application, so a bean that only works on the web
 * (such as the security config) could break the loader without them noticing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class LoadModeTest {

  @DynamicPropertySource
  static void loadFile(DynamicPropertyRegistry registry) {
    registry.add("app.load.file", () -> StandardData.file().toString());
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void startsWithoutAWebServerAndLoadsTheFile() {
    assertThat(jdbc.queryForObject("select count(*) from patient", Integer.class))
        .isEqualTo(StandardData.PATIENTS);
  }
}
