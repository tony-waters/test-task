package uk.bit1.engagement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;

/**
 * The layer rules from NOTES.md: dependencies point domain ← application ← web. The web layer may
 * use domain types such as {@code Status} directly, but reads data only through application
 * services. The importer and security sit beside the layers and nothing depends on them.
 */
@AnalyzeClasses(packages = "uk.bit1.engagement", importOptions = ImportOption.DoNotIncludeTests.class)
class LayerDependencyTest {

  private static final String ROOT = "uk.bit1.engagement.";

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain").definedBy(ROOT + "domain..")
          .layer("Application").definedBy(ROOT + "application..")
          .layer("Web").definedBy(ROOT + "web..")
          .layer("Importer").definedBy(ROOT + "importer..")
          .layer("Security").definedBy(ROOT + "security..")
          .whereLayer("Web").mayOnlyBeAccessedByLayers("Security")
          .whereLayer("Application").mayOnlyBeAccessedByLayers("Web")
          .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Web", "Importer")
          .whereLayer("Importer").mayNotBeAccessedByAnyLayer()
          .whereLayer("Security").mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule webReadsThroughServices =
      noClasses()
          .that().resideInAPackage(ROOT + "web..")
          .should().dependOnClassesThat().areAssignableTo(Repository.class)
          .because("controllers go through the application services, never a repository");
}
