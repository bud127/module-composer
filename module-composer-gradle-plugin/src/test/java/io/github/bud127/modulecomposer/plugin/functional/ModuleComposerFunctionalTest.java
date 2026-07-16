package io.github.bud127.modulecomposer.plugin.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleComposerFunctionalTest {

    @TempDir
    Path projectDir;

    @Test
    void pluginLoadsSuccessfully() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = run("tasks");

        assertTrue(result.getOutput().contains("bundleRun"));
        assertTrue(result.getOutput().contains("bundleBuild"));
        assertTrue(result.getOutput().contains("bundleTest"));
    }

    @Test
    void modulePluginRegistersMetadata() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = run("listModules");

        assertTrue(result.getOutput().contains("+ payment"));
        assertTrue(result.getOutput().contains("project: :module-payment"));
        assertTrue(result.getOutput().contains("configuration: example.payment.PaymentConfiguration"));
    }

    @Test
    void duplicateModuleNamesFail() throws IOException {
        writeProject(List.of("payment", "duplicate-payment"), false);
        writeModule("module-duplicate-payment", "payment");

        BuildResult result = fail("listModules");

        assertTrue(result.getOutput().contains("Duplicate Module Composer module name 'payment'"));
    }

    @Test
    void listModulesWorksWithoutYaml() throws IOException {
        writeProject(List.of("payment", "notification"), false);

        BuildResult result = run("listModules");

        assertTrue(result.getOutput().contains("+ payment"));
        assertTrue(result.getOutput().contains("+ notification"));
    }

    @Test
    void cliModuleWorksWithoutYaml() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = run("bundleRun", "-Pmodules=payment");

        assertEquals(SUCCESS, result.task(":module-payment:bootRun").getOutcome());
    }

    @Test
    void multipleCliModulesWireGeneratedHostWithoutYaml() throws IOException {
        writeProject(List.of("payment", "notification"), false);

        BuildResult result = run(
                "bundleBuild",
                "-Pmodules=payment,notification",
                "--dry-run"
        );

        assertTrue(result.getOutput().contains(":prepareGeneratedHost SKIPPED"));
        assertTrue(result.getOutput().contains(":copyGeneratedHostJar SKIPPED"));
    }

    @ParameterizedTest(name = "{1} validation")
    @MethodSource("validationModes")
    void validationRunsSelectedModuleTasks(
            List<String> projectModules,
            String validation,
            String selectedModules,
            List<String> expectedOutputs
    ) throws IOException {
        writeProject(projectModules, false);

        BuildResult result = run(
                "bundleBuild",
                "-Pmodules=" + selectedModules,
                "-Pvalidation=" + validation,
                "--dry-run"
        );

        assertOutputContains(result, expectedOutputs);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bundleTestSelections")
    void bundleTestWiresExpectedTasks(
            String scenario,
            List<String> projectModules,
            String selectedModules,
            List<String> expectedOutputs
    ) throws IOException {
        writeProject(projectModules, false);

        BuildResult result = run(
                "bundleTest",
                "-Pmodules=" + selectedModules,
                "--dry-run"
        );

        assertOutputContains(result, expectedOutputs);
    }

    @Test
    void invalidValidationModeFails() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = fail(
                "bundleBuild",
                "-Pmodules=payment",
                "-Pvalidation=verify"
        );

        assertTrue(result.getOutput().contains("Invalid validation 'verify'"));
    }

    @Test
    void distributionRequiresYaml() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = fail("bundleRun", "-Pdistribution=community");

        assertTrue(result.getOutput().contains("Distribution preset was requested"));
        assertTrue(result.getOutput().contains("Distribution presets are optional for -Pmodules"));
    }

    @Test
    void missingDistributionYamlProducesReadableListMessage() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = run("listDistributions");

        assertTrue(result.getOutput().contains("No distributions.yml file found."));
        assertTrue(result.getOutput().contains("Distribution presets are optional."));
    }

    @Test
    void unknownDistributionFails() throws IOException {
        writeProject(List.of("payment"), true);

        BuildResult result = fail("bundleRun", "-Pdistribution=missing");

        assertTrue(result.getOutput().contains("Unknown distribution 'missing'"));
        assertTrue(result.getOutput().contains("community"));
    }

    @Test
    void unknownModuleInDistributionFails() throws IOException {
        writeProject(List.of("payment"), false);
        writeDistribution("""
                version: 1
                distributions:
                  community:
                    modules:
                      - unknown
                """);

        BuildResult result = fail("bundleRun", "-Pdistribution=community");

        assertTrue(result.getOutput().contains("Unknown module 'unknown'"));
        assertTrue(result.getOutput().contains("Available modules: [payment]"));
    }

    @Test
    void cliModulesAndDistributionTogetherFail() throws IOException {
        writeProject(List.of("payment"), true);

        BuildResult result = fail(
                "bundleRun",
                "-Pmodules=payment",
                "-Pdistribution=community"
        );

        assertTrue(result.getOutput().contains("Use either -Pmodules or -Pdistribution, not both"));
    }

    @Test
    void singleModuleDistributionUsesStandaloneMode() throws IOException {
        writeProject(List.of("payment", "notification"), true);

        BuildResult result = run("explain", "-Pdistribution=payment-only");

        assertTrue(result.getOutput().contains("Distribution   : payment-only"));
        assertTrue(result.getOutput().contains("Execution      : STANDALONE"));
        assertTrue(result.getOutput().contains("Framework      : spring-boot"));
        assertTrue(result.getOutput().contains("Adapter        : SpringBootFrameworkAdapter"));
        assertTrue(result.getOutput().contains(":module-payment:bootRun"));
    }

    @Test
    void includeAndExcludeOverridesApplyInOrder() throws IOException {
        writeProject(List.of("payment", "notification", "audit"), true);

        BuildResult result = run(
                "explain",
                "-Pdistribution=community",
                "-PincludeModules=audit",
                "-PexcludeModules=notification"
        );

        assertTrue(result.getOutput().contains("+ payment"));
        assertTrue(result.getOutput().contains("+ audit"));
        assertTrue(!result.getOutput().contains("+ notification"));
    }

    @Test
    void allModulesExcludedFails() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = fail(
                "bundleRun",
                "-Pmodules=payment",
                "-PexcludeModules=payment"
        );

        assertTrue(result.getOutput().contains("No modules remain after overrides."));
    }

    @Test
    void invalidPortFails() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = fail(
                "bundleRun",
                "-Pmodules=payment",
                "-Pport=70000"
        );

        assertTrue(result.getOutput().contains("Invalid port '70000'"));
    }

    @Test
    void explainShowsStandalonePlan() throws IOException {
        writeProject(List.of("payment"), false);

        BuildResult result = run("explain", "-Pmodules=payment");

        assertTrue(result.getOutput().contains("Selection mode : CLI"));
        assertTrue(result.getOutput().contains("Execution      : STANDALONE"));
        assertTrue(result.getOutput().contains("Validation     : none"));
        assertTrue(result.getOutput().contains("Port           : default"));
        assertTrue(result.getOutput().contains(":module-payment:bootRun"));
    }

    @Test
    void explainShowsGeneratedHostPlan() throws IOException {
        writeProject(List.of("payment", "notification"), false);

        BuildResult result = run(
                "explain",
                "-Pmodules=payment,notification",
                "-Pport=9090"
        );

        assertTrue(result.getOutput().contains("Selection mode : CLI"));
        assertTrue(result.getOutput().contains("Execution      : GENERATED_HOST"));
        assertTrue(result.getOutput().contains("Adapter        : SpringBootFrameworkAdapter"));
        assertTrue(result.getOutput().contains("Port           : 9090"));
        assertTrue(result.getOutput().contains("build/module-composer/generated/combined-app"));
        assertTrue(result.getOutput().contains("build/module-composer/output/combined-app.jar"));
    }

    @Test
    void distributionApplicationNameSetsGeneratedBundleName() throws IOException {
        writeProject(List.of("payment", "notification"), false);
        writeDistribution("""
                version: 1
                distributions:
                  community:
                    applicationName: community-service
                    modules:
                      - payment
                      - notification
                """);

        BuildResult result = run(
                "explain",
                "-Pdistribution=community"
        );

        assertTrue(result.getOutput().contains("Application    : community-service"));
        assertTrue(result.getOutput().contains("build/module-composer/generated/community-service"));
        assertTrue(result.getOutput().contains("build/module-composer/output/community-service.jar"));
    }

    @Test
    void singleDistributionFileCanSetArtifactAndContainerMetadata()
            throws IOException {
        writeProject(List.of("document", "email", "upload"), false);
        write("distributions/document-platform.yaml", """
                name: document-platform
                version: 0.2.0

                modules:
                  - document
                  - email
                  - upload

                artifact:
                  fileName: application.jar

                container:
                  image: ghcr.io/bud127/document-platform
                  baseImage: amazoncorretto:21-alpine
                  hostPort: 9090
                  containerPort: 8080
                """);

        BuildResult result = run(
                "explain",
                "-Pdistribution=document-platform"
        );

        assertTrue(result.getOutput().contains("Application    : document-platform"));
        assertTrue(result.getOutput().contains("Artifact       : application.jar"));
        assertTrue(result.getOutput().contains("Container      : ghcr.io/bud127/document-platform:9090->8080"));
        assertTrue(result.getOutput().contains("build/module-composer/generated/document-platform"));
        assertTrue(result.getOutput().contains("build/module-composer/output/application.jar"));
    }

    @Test
    void bundleBuildWritesContainerFilesUnderApplicationSpecificDirectory()
            throws IOException {
        writeProject(List.of("document", "email"), false);
        writeExecutableGradleWrapper();
        write("distributions/document-platform.yaml", """
                name: document-platform
                version: 0.2.0

                modules:
                  - document
                  - email

                artifact:
                  fileName: application.jar

                container:
                  image: ghcr.io/bud127/document-platform
                  baseImage: amazoncorretto:21-alpine
                  hostPort: 9090
                  containerPort: 8080
                """);

        run("bundleBuild", "-Pdistribution=document-platform");

        Path containerDirectory = projectDir.resolve(
                "build/module-composer/output/containers/document-platform"
        );
        String dockerfile = Files.readString(
                containerDirectory.resolve("Dockerfile")
        );
        String compose = Files.readString(
                containerDirectory.resolve("docker-compose.yml")
        );

        assertTrue(dockerfile.contains("FROM amazoncorretto:21-alpine"));
        assertTrue(dockerfile.contains("ARG JAR_FILE=application.jar"));
        assertTrue(dockerfile.contains("EXPOSE 8080"));
        assertTrue(compose.contains("context: ../.."));
        assertTrue(compose.contains("dockerfile: containers/document-platform/Dockerfile"));
        assertTrue(compose.contains("JAR_FILE: application.jar"));
        assertTrue(compose.contains("image: ghcr.io/bud127/document-platform"));
        assertTrue(compose.contains("\"9090:8080\""));
    }

    @Test
    void distributionWithoutContainerDoesNotReportContainerMetadata()
            throws IOException {
        writeProject(List.of("document", "email"), false);
        write("distributions/document-platform.yaml", """
                name: document-platform
                version: 0.2.0

                modules:
                  - document
                  - email

                artifact:
                  fileName: application.jar
                """);

        BuildResult result = run(
                "explain",
                "-Pdistribution=document-platform"
        );

        assertTrue(result.getOutput().contains("Artifact       : application.jar"));
        assertTrue(!result.getOutput().contains("Container      :"));
    }

    @Test
    void cliApplicationNameOverridesDistributionApplicationName() throws IOException {
        writeProject(List.of("payment", "notification"), false);
        writeDistribution("""
                version: 1
                distributions:
                  community:
                    applicationName: community-service
                    modules:
                      - payment
                      - notification
                """);

        BuildResult result = run(
                "explain",
                "-Pdistribution=community",
                "-PapplicationName=cli-service"
        );

        assertTrue(result.getOutput().contains("Application    : cli-service"));
        assertTrue(result.getOutput().contains("build/module-composer/generated/cli-service"));
        assertTrue(result.getOutput().contains("build/module-composer/output/cli-service.jar"));
    }

    @Test
    void invalidApplicationNameFails() throws IOException {
        writeProject(List.of("payment", "notification"), false);

        BuildResult result = fail(
                "explain",
                "-Pmodules=payment,notification",
                "-PapplicationName=bad/name"
        );

        assertTrue(result.getOutput().contains("Invalid application name 'bad/name'"));
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult fail(String... arguments) {
        return runner(arguments).buildAndFail();
    }

    private static Stream<Arguments> validationModes() {
        return Stream.of(
                Arguments.of(
                        List.of("payment", "notification"),
                        "test",
                        "payment,notification",
                        List.of(
                                ":module-payment:test SKIPPED",
                                ":module-notification:test SKIPPED",
                                ":prepareGeneratedHost SKIPPED"
                        )
                ),
                Arguments.of(
                        List.of("payment"),
                        "check",
                        "payment",
                        List.of(
                                ":module-payment:check SKIPPED",
                                ":module-payment:bootJar SKIPPED"
                        )
                )
        );
    }

    private static Stream<Arguments> bundleTestSelections() {
        return Stream.of(
                Arguments.of(
                        "standalone module",
                        List.of("payment"),
                        "payment",
                        List.of(":module-payment:test SKIPPED")
                ),
                Arguments.of(
                        "generated host",
                        List.of("payment", "notification"),
                        "payment,notification",
                        List.of(
                                ":prepareGeneratedHost SKIPPED",
                                ":testGeneratedHost SKIPPED"
                        )
                )
        );
    }

    private static void assertOutputContains(
            BuildResult result,
            List<String> expectedOutputs
    ) {
        expectedOutputs.forEach(
                expected -> assertTrue(result.getOutput().contains(expected))
        );
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withArguments(arguments)
                .withPluginClasspath()
                .forwardOutput();
    }

    private void writeProject(
            List<String> modules,
            boolean withDistribution
    ) throws IOException {
        StringBuilder includes = new StringBuilder();
        for (String module : modules) {
            includes.append("include(\":module-")
                    .append(module)
                    .append("\")\n");
        }

        write("settings.gradle.kts", """
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        mavenCentral()
                    }
                }

                dependencyResolutionManagement {
                    repositories {
                        mavenCentral()
                    }
                }

                rootProject.name = "test-module-composer"
                %s
                """.formatted(includes));

        write("build.gradle.kts", """
                plugins {
                    id("io.github.bud127.module-composer")
                }

                moduleComposer {
                    javaVersion.set(21)
                }
                """);

        for (String module : modules) {
            writeModule("module-" + module, module);
        }

        if (withDistribution) {
            writeDistribution("""
                    version: 1
                    distributions:
                      payment-only:
                        modules:
                          - payment
                      community:
                        applicationName: community-service
                        modules:
                          - payment
                          - notification
                      enterprise:
                        modules:
                          - payment
                          - notification
                          - audit
                    """);
        }
    }

    private void writeModule(String projectName, String moduleName)
            throws IOException {
        write(projectName + "/build.gradle.kts", """
                plugins {
                    java
                    id("io.github.bud127.module-composer-module")
                }

                moduleComposerModule {
                    name.set("%s")
                    configurationClass.set("example.%s.%sConfiguration")
                    standaloneRunTask.set("bootRun")
                    standaloneBuildTask.set("bootJar")
                }

                tasks.register("bootRun") {
                    doLast {
                        println("bootRun %s")
                    }
                }

                tasks.register("bootJar") {
                    doLast {
                        println("bootJar %s")
                    }
                }

                tasks.named("test") {
                    doLast {
                        println("test %s")
                    }
                }

                tasks.named("check") {
                    doLast {
                        println("check %s")
                    }
                }
                """.formatted(
                moduleName,
                moduleName,
                capitalize(moduleName),
                moduleName,
                moduleName,
                moduleName,
                moduleName
        ));

        write(projectName + "/src/main/java/example/" +
                moduleName +
                "/" +
                capitalize(moduleName) +
                "Configuration.java", """
                package example.%s;

                public class %sConfiguration {
                }
                """.formatted(moduleName, capitalize(moduleName)));
    }

    private void writeDistribution(String content) throws IOException {
        write("distributions.yml", content);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = projectDir.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void writeExecutableGradleWrapper() throws IOException {
        Path file = projectDir.resolve("gradlew");
        Files.writeString(file, """
                #!/usr/bin/env sh
                set -eu
                mkdir -p build/libs
                printf 'dummy jar' > build/libs/combined-app.jar
                """);
        assertTrue(file.toFile().setExecutable(true));
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
