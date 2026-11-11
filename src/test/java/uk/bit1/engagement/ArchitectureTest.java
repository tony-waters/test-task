package uk.bit1.engagement;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import uk.bit1.engagement.web.RequestLogFilter;

/** The package rules from ADR 0003: dependencies point one way, towards {@code core} and {@code shared}. */
@AnalyzeClasses(packages = "uk.bit1.engagement", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String APP = "uk.bit1.engagement..";
  private static final String CORE = "uk.bit1.engagement.core..";
  private static final String SHARED = "uk.bit1.engagement.shared..";
  private static final String WEB = "uk.bit1.engagement.web..";
  private static final String SECURITY = "uk.bit1.engagement.security..";

  @ArchTest
  static final ArchRule sharedDependsOnNothingElseInTheApp =
      noClasses()
          .that()
          .resideInAPackage(SHARED)
          .should()
          .dependOnClassesThat(resideInAPackage(APP).and(not(resideInAPackage(SHARED))));

  @ArchTest
  static final ArchRule coreDependsOnlyOnShared =
      noClasses()
          .that()
          .resideInAPackage(CORE)
          .should()
          .dependOnClassesThat(resideInAPackage(APP).and(not(resideInAnyPackage(CORE, SHARED))));

  @ArchTest
  static final ArchRule onlyCrossCuttingCodeUsesWeb =
      noClasses()
          .that()
          .resideOutsideOfPackages(WEB, SECURITY)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(WEB);

  @ArchTest
  static final ArchRule securityUsesOnlyTheRequestLogFromWeb =
      noClasses()
          .that()
          .resideInAPackage(SECURITY)
          .should()
          .dependOnClassesThat(
              resideInAPackage(WEB).and(not(belongToAnyOf(RequestLogFilter.class))));

  @ArchTest
  static final ArchRule useCasesAreFreeOfCycles =
      slices().matching("uk.bit1.engagement.patients.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule topLevelPackagesAreFreeOfCycles =
      slices().matching("uk.bit1.engagement.(*)..").should().beFreeOfCycles();
}
