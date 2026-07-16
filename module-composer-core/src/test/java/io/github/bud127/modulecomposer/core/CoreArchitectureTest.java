package io.github.bud127.modulecomposer.core;

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

import static org.junit.jupiter.api.Assertions.*;

class CoreArchitectureTest {

    @TempDir
    Path directory;

    @Test
    void registryRejectsDuplicateModuleNames() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("payment", ":module-payment"));
        ModuleRegistration duplicateModule = module("payment", ":other-payment");

        ModuleComposerException exception = assertThrows(
                ModuleComposerException.class,
                () -> registry.register(duplicateModule)
        );

        assertTrue(exception.getMessage().contains("Duplicate Module Composer module name 'payment'"));
    }

    @Test
    void selectorResolvesCliModulesWithoutYaml() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("payment", ":module-payment"));

        ModuleSelection selection = new ModuleSelector(
                registry,
                new DistributionLoader(directory.resolve("missing.yml")),
                path -> true
        ).resolve(new SelectionRequest(
                List.of("payment"),
                null,
                null,
                List.of(),
                List.of(),
                RuntimeOptions.none()
        ));

        assertEquals(List.of("payment"), selection.moduleNames());
        assertEquals(SelectionMode.CLI, selection.mode());
    }

    @Test
    void selectorAppliesIncludeAndExclude() throws IOException {
        Path yaml = directory.resolve("distributions.yml");
        Files.writeString(yaml, """
                version: 1
                distributions:
                  community:
                    modules:
                      - payment
                      - notification
                """);

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("payment", ":module-payment"));
        registry.register(module("notification", ":module-notification"));
        registry.register(module("audit", ":module-audit"));

        ModuleSelection selection = new ModuleSelector(
                registry,
                new DistributionLoader(yaml),
                path -> true
        ).resolve(new SelectionRequest(
                List.of(),
                "community",
                null,
                List.of("audit"),
                List.of("notification"),
                RuntimeOptions.none()
        ));

        assertEquals(List.of("payment", "audit"), selection.moduleNames());
    }

    @Test
    void selectorUsesApplicationNameFromDistribution() throws IOException {
        Path yaml = directory.resolve("distributions.yml");
        Files.writeString(yaml, """
                version: 1
                distributions:
                  community:
                    applicationName: community-service
                    modules:
                      - payment
                """);

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("payment", ":module-payment"));

        ModuleSelection selection = new ModuleSelector(
                registry,
                new DistributionLoader(yaml),
                path -> true
        ).resolve(new SelectionRequest(
                List.of(),
                "community",
                null,
                List.of(),
                List.of(),
                RuntimeOptions.none()
        ));

        assertEquals("community-service", selection.applicationName());
    }

    @Test
    void cliApplicationNameOverridesDistributionApplicationName() throws IOException {
        Path yaml = directory.resolve("distributions.yml");
        Files.writeString(yaml, """
                version: 1
                distributions:
                  community:
                    applicationName: community-service
                    modules:
                      - payment
                """);

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("payment", ":module-payment"));

        ModuleSelection selection = new ModuleSelector(
                registry,
                new DistributionLoader(yaml),
                path -> true
        ).resolve(new SelectionRequest(
                List.of(),
                "community",
                "cli-service",
                List.of(),
                List.of(),
                RuntimeOptions.none()
        ));

        assertEquals("cli-service", selection.applicationName());
    }

    @Test
    void selectorResolvesSingleDistributionFileWithArtifactAndContainer()
            throws IOException {
        Path yaml = directory.resolve("distributions/document-platform.yaml");
        Files.createDirectories(yaml.getParent());
        Files.writeString(yaml, """
                name: document-platform
                version: 0.1.0

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

        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("document", ":module-document"));
        registry.register(module("email", ":module-email"));
        registry.register(module("upload", ":module-upload"));

        ModuleSelection selection = new ModuleSelector(
                registry,
                new DistributionLoader(directory.resolve("distributions.yml")),
                path -> true
        ).resolve(new SelectionRequest(
                List.of(),
                "document-platform",
                null,
                List.of(),
                List.of(),
                RuntimeOptions.none()
        ));

        assertEquals(
                List.of("document", "email", "upload"),
                selection.moduleNames()
        );
        assertEquals("document-platform", selection.applicationName());
        assertEquals("application.jar", selection.artifact().fileName());
        assertEquals(
                "ghcr.io/bud127/document-platform",
                selection.container().image()
        );
        assertEquals("amazoncorretto:21-alpine", selection.container().baseImage());
        assertEquals(9090, selection.container().hostPort());
        assertEquals(8080, selection.container().containerPort());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDistributionSchemas")
    void distributionRejectsInvalidSchema(
            String scenario,
            String yamlContent,
            List<String> expectedMessages
    ) throws IOException {
        Path yaml = directory.resolve("distributions.yml");
        Files.writeString(yaml, yamlContent);
        DistributionLoader loader = new DistributionLoader(yaml);

        ModuleComposerException exception = assertThrows(
                ModuleComposerException.class,
                loader::loadRequired
        );

        expectedMessages.forEach(
                expected -> assertTrue(exception.getMessage().contains(expected))
        );
    }

    @Test
    void plannerCreatesGeneratedHostPlanForMultipleModules() {
        ModuleSelection selection = new ModuleSelection(
                List.of(
                        module("payment", ":module-payment"),
                        module("notification", ":module-notification")
                ),
                RuntimeOptions.none(),
                SelectionMode.CLI,
                new DistributionDetails(
                        null,
                        "combined-app",
                        null,
                        null
                )
        );

        CompositionPlan plan =
                new CompositionPlanner().plan("spring-boot", selection);

        assertEquals(ExecutionMode.GENERATED_HOST, plan.executionMode());
        assertEquals("spring-boot", plan.framework());
        assertEquals("combined-app", plan.applicationName());
    }

    @Test
    void adapterRegistryLooksUpRegisteredAdapter() {
        FrameworkAdapter adapter = new TestFrameworkAdapter();
        FrameworkAdapterRegistry registry = new FrameworkAdapterRegistry();
        registry.register(adapter);

        assertEquals(adapter, registry.require("test"));
        assertThrows(
                ModuleComposerException.class,
                () -> registry.require("missing")
        );
    }

    @Test
    void buildToolAdapterTranslatesPlanToInvocations() {
        BuildToolAdapter adapter = new TestBuildToolAdapter();
        FrameworkAdapter framework = new TestFrameworkAdapter();
        ModuleRegistration module = module("payment", ":module-payment");

        assertEquals("test-build-tool", adapter.buildToolId());
        assertTrue(adapter.projectExists(module.projectPath()));
        assertEquals(
                List.of("run-payment"),
                adapter.standaloneRun(framework, module).steps()
        );
        assertEquals(
                List.of("package-payment"),
                adapter.moduleArtifact(module).steps()
        );
        assertEquals(
                List.of("prepare", "run-generated"),
                adapter.generatedRun(framework).steps()
        );
    }

    private static Stream<Arguments> invalidDistributionSchemas() {
        return Stream.of(
                Arguments.of(
                        "unknown root field",
                        """
                        version: 1
                        unknown: true
                        distributions:
                          community:
                            modules:
                              - payment
                        """,
                        List.of("Unknown field 'unknown'", "distribution root")
                ),
                Arguments.of(
                        "unknown preset field",
                        """
                        version: 1
                        distributions:
                          community:
                            modules:
                              - payment
                            extra: true
                        """,
                        List.of("Unknown field 'extra'", "distribution 'community'")
                ),
                Arguments.of(
                        "invalid version shape",
                        """
                        version:
                          major: 1
                        distributions:
                          community:
                            modules:
                              - payment
                        """,
                        List.of("Field 'version'", "non-empty string or number")
                ),
                Arguments.of(
                        "duplicate modules",
                        """
                        version: 1
                        distributions:
                          community:
                            modules:
                              - payment
                              - payment
                        """,
                        List.of("duplicate module 'payment'")
                ),
                Arguments.of(
                        "unknown artifact field",
                        """
                        version: 1
                        distributions:
                          community:
                            modules:
                              - payment
                            artifact:
                              fileName: application.jar
                              classifier: boot
                        """,
                        List.of("Unknown field 'classifier'", "artifact")
                ),
                Arguments.of(
                        "unknown container field",
                        """
                        version: 1
                        distributions:
                          community:
                            modules:
                              - payment
                            container:
                              image: bud127/community
                              port: 8080
                        """,
                        List.of("Unknown field 'port'", "container")
                )
        );
    }

    private static ModuleRegistration module(String name, String projectPath) {
        return new ModuleRegistration(
                name,
                projectPath,
                "example." + name + ".Configuration",
                projectPath + ":bootRun",
                projectPath + ":bootJar",
                projectPath + ":jar"
        );
    }

    private static final class TestFrameworkAdapter implements FrameworkAdapter {
        @Override
        public String frameworkId() {
            return "test";
        }

        @Override
        public void generateHost(
                CompositionPlan plan,
                GeneratedHostContext context
        ) {
            throw new UnsupportedOperationException(
                    "Test adapter does not generate host files for " +
                            plan.framework() + "/" + context.applicationName() + "."
            );
        }

        @Override
        public String standaloneRunTask(ModuleRegistration module) {
            return module.standaloneRunTask();
        }

        @Override
        public String standaloneBuildTask(ModuleRegistration module) {
            return module.standaloneBuildTask();
        }

        @Override
        public String generatedRunTask() {
            return "run";
        }

        @Override
        public String generatedBuildTask() {
            return "build";
        }

        @Override
        public Path generatedArtifact() {
            return Path.of("build/output.jar");
        }
    }

    private static final class TestBuildToolAdapter implements BuildToolAdapter {

        @Override
        public String buildToolId() {
            return "test-build-tool";
        }

        @Override
        public boolean projectExists(String projectReference) {
            return projectReference.startsWith(":module-");
        }

        @Override
        public BuildInvocation standaloneRun(
                FrameworkAdapter frameworkAdapter,
                ModuleRegistration module
        ) {
            return BuildInvocation.of(
                    frameworkAdapter.frameworkId() + "-run",
                    "run-" + module.name()
            );
        }

        @Override
        public BuildInvocation standaloneBuild(
                FrameworkAdapter frameworkAdapter,
                ModuleRegistration module
        ) {
            return BuildInvocation.of(
                    frameworkAdapter.frameworkId() + "-build",
                    "build-" + module.name()
            );
        }

        @Override
        public BuildInvocation moduleArtifact(ModuleRegistration module) {
            return BuildInvocation.of("artifact", "package-" + module.name());
        }

        @Override
        public BuildInvocation projectArtifact(String projectReference) {
            return BuildInvocation.of(
                    "artifact",
                    "package-" + projectReference.substring(1)
            );
        }

        @Override
        public BuildInvocation generatedRun(FrameworkAdapter frameworkAdapter) {
            return new BuildInvocation(
                    frameworkAdapter.frameworkId() + "-generated-run",
                    List.of("prepare", "run-generated")
            );
        }

        @Override
        public BuildInvocation generatedBuild(FrameworkAdapter frameworkAdapter) {
            return new BuildInvocation(
                    frameworkAdapter.frameworkId() + "-generated-build",
                    List.of("prepare", "build-generated")
            );
        }
    }
}
