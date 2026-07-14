package io.github.bud127.modulecomposer.plugin.functional;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        assertTrue(result.getOutput().contains("build/module-composer/combined-app"));
        assertTrue(result.getOutput().contains("build/module-composer/output/combined-app.jar"));
    }

    private BuildResult run(String... arguments) {
        return runner(arguments).build();
    }

    private BuildResult fail(String... arguments) {
        return runner(arguments).buildAndFail();
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
                    springBootVersion.set("3.5.7")
                    dependencyManagementVersion.set("1.1.7")
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
                """.formatted(
                moduleName,
                moduleName,
                capitalize(moduleName),
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

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }
}
