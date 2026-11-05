package uk.bit1.engagement;

import java.time.Clock;
import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Runs the web application, or, when {@code app.load.file} is set, loads the dump at that path and
 * exits.
 */
@SpringBootApplication
public class EngagementApplication {

  static final String LOAD_FILE_PROPERTY = "app.load.file";

  public static void main(String[] args) {
    boolean loadMode = isLoadMode(args);
    SpringApplication app = new SpringApplication(EngagementApplication.class);
    if (loadMode) {
      app.setWebApplicationType(WebApplicationType.NONE);
    }
    ConfigurableApplicationContext context = app.run(args);
    if (loadMode) {
      System.exit(SpringApplication.exit(context));
    }
  }

  static boolean isLoadMode(String[] args) {
    boolean inArgs = Arrays.stream(args).anyMatch(a -> a.startsWith("--" + LOAD_FILE_PROPERTY + "="));
    String fromEnv = System.getenv("APP_LOAD_FILE");
    return inArgs || (fromEnv != null && !fromEnv.isBlank());
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
