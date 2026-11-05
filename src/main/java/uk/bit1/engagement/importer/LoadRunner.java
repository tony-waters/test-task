package uk.bit1.engagement.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Load mode: {@code --app.load.file=<path>} loads that dump once. The application then exits. */
@Component
@ConditionalOnProperty("app.load.file")
class LoadRunner implements ApplicationRunner {

  private final DumpLoader loader;
  private final Path file;

  LoadRunner(DumpLoader loader, @Value("${app.load.file}") Path file) {
    this.loader = loader;
    this.file = file;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!Files.isReadable(file)) {
      throw new IllegalStateException("app.load.file is not a readable file: " + file);
    }
    loader.load(file);
  }
}
