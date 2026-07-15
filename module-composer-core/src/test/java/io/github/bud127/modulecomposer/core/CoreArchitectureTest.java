package io.github.bud127.modulecomposer.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoreArchitectureTest {

    @TempDir
    Path directory;

    @Test
    void registryRejectsDuplicateModuleNames() {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(module("payment", ":module-payment"));

        ModuleComposerException exception = assertThrows(
                ModuleComposerException.class,
                () -> registry.register(module("payment", ":other-payment"))
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
    void plannerCreatesGeneratedHostPlanForMultipleModules() {
        ModuleSelection selection = new ModuleSelection(
                List.of(
                        module("payment", ":module-payment"),
                        module("notification", ":module-notification")
                ),
                null,
                "combined-app",
                RuntimeOptions.none(),
                SelectionMode.CLI
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
            return BuildInvocation.of("run", "run-" + module.name());
        }

        @Override
        public BuildInvocation standaloneBuild(
                FrameworkAdapter frameworkAdapter,
                ModuleRegistration module
        ) {
            return BuildInvocation.of("build", "build-" + module.name());
        }

        @Override
        public BuildInvocation moduleArtifact(ModuleRegistration module) {
            return BuildInvocation.of("artifact", "package-" + module.name());
        }

        @Override
        public BuildInvocation projectArtifact(String projectReference) {
            return BuildInvocation.of("artifact", "package-common");
        }

        @Override
        public BuildInvocation generatedRun(FrameworkAdapter frameworkAdapter) {
            return new BuildInvocation("generated-run", List.of("prepare", "run-generated"));
        }

        @Override
        public BuildInvocation generatedBuild(FrameworkAdapter frameworkAdapter) {
            return new BuildInvocation("generated-build", List.of("prepare", "build-generated"));
        }
    }
}
