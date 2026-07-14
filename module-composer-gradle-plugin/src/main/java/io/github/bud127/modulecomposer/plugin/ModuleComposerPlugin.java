package io.github.bud127.modulecomposer.plugin;

import io.github.bud127.modulecomposer.core.*;
import io.github.bud127.modulecomposer.module.ModuleComposerModulePlugin;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Exec;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Root Gradle plugin that resolves module selections and wires bundle tasks.
 */
public final class ModuleComposerPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new GradleException(
                    "Module Composer must be applied to the root project."
            );
        }

        ModuleRegistry registry = ModuleComposerModulePlugin.registry(project);

        ModuleComposerExtension extension = project.getExtensions().create(
                "moduleComposer",
                ModuleComposerExtension.class
        );

        TaskProvider<Task> bundleRun = registerTask(
                project,
                "bundleRun",
                "Runs one module directly or a generated combined host."
        );

        TaskProvider<Task> bundleBuild = registerTask(
                project,
                "bundleBuild",
                "Builds one module directly or a generated combined JAR."
        );

        TaskProvider<Task> explain = registerTask(
                project,
                "explain",
                "Explains the selected execution and build plan."
        );

        registerDiscoveryTasks(project, extension, registry);

        project.getGradle().projectsEvaluated(gradle -> {
            if (!selectionTaskRequested(project)) {
                return;
            }

            FrameworkAdapter adapter = adapterRegistry(extension)
                    .require(extension.getFramework().get());
            GradleBuildToolAdapter buildTool =
                    new GradleBuildToolAdapter(project);

            CompositionPlan plan = buildPlan(
                    project,
                    extension,
                    registry,
                    adapter,
                    buildTool
            );

            explain.configure(task ->
                    task.doLast(ignored ->
                            explainPlan(project, extension, adapter, buildTool, plan)
                    )
            );

            if (plan.isStandalone()) {
                configureStandalone(project, buildTool, adapter, plan, bundleRun, bundleBuild);
            } else {
                configureGeneratedHost(
                        project,
                        extension,
                        buildTool,
                        adapter,
                        plan,
                        bundleRun,
                        bundleBuild
                );
            }
        });
    }

    private FrameworkAdapterRegistry adapterRegistry(
            ModuleComposerExtension extension
    ) {
        FrameworkAdapterRegistry registry = new FrameworkAdapterRegistry();
        ServiceLoader.load(
                        FrameworkAdapter.class,
                        ModuleComposerPlugin.class.getClassLoader()
                )
                .forEach(registry::register);
        return registry;
    }

    private CompositionPlan buildPlan(
            Project project,
            ModuleComposerExtension extension,
            ModuleRegistry registry,
            FrameworkAdapter adapter,
            GradleBuildToolAdapter buildTool
    ) {
        try {
            DistributionLoader loader =
                    new DistributionLoader(distributionPath(project, extension));
            ModuleSelector selector = new ModuleSelector(
                    registry,
                    loader,
                    buildTool::projectExists
            );
            ModuleSelection selection = selector.resolve(selectionRequest(project));
            return new CompositionPlanner().plan(adapter.frameworkId(), selection);
        } catch (ModuleComposerException exception) {
            throw new GradleException(exception.getMessage(), exception);
        }
    }

    private TaskProvider<Task> registerTask(
            Project project,
            String name,
            String description
    ) {
        return project.getTasks().register(name, task -> {
            task.setGroup("module composer");
            task.setDescription(description);
        });
    }

    private void registerDiscoveryTasks(
            Project project,
            ModuleComposerExtension extension,
            ModuleRegistry registry
    ) {
        project.getTasks().register("listModules", task -> {
            task.setGroup("module composer");
            task.doLast(ignored -> {
                project.getLogger().lifecycle("Available modules:");
                if (registry.all().isEmpty()) {
                    project.getLogger().lifecycle("  (none registered)");
                    return;
                }

                registry.all().forEach(module -> {
                    project.getLogger().lifecycle("  + {}", module.name());
                    project.getLogger().lifecycle(
                            "      project: {}",
                            module.projectPath()
                    );
                    project.getLogger().lifecycle(
                            "      configuration: {}",
                            module.configurationClass()
                    );
                });
            });
        });

        project.getTasks().register("listDistributions", task -> {
            task.setGroup("module composer");
            task.doLast(ignored -> {
                DistributionLoader loader =
                        new DistributionLoader(distributionPath(project, extension));

                if (!loader.exists()) {
                    project.getLogger().lifecycle(
                            "No {} file found.",
                            extension.getDistributionFile().get()
                    );
                    project.getLogger().lifecycle(
                            "Distribution presets are optional."
                    );
                    return;
                }

                DistributionConfig config = loader.loadRequired();

                project.getLogger().lifecycle("Available distributions:");
                config.distributions().keySet().forEach(
                        name -> project.getLogger().lifecycle("  + {}", name)
                );
            });
        });
    }

    private void configureStandalone(
            Project root,
            GradleBuildToolAdapter buildTool,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            TaskProvider<Task> bundleRun,
            TaskProvider<Task> bundleBuild
    ) {
        ModuleRegistration module = plan.modules().get(0);
        BuildInvocation run = buildTool.standaloneRun(adapter, module);
        BuildInvocation build = buildTool.standaloneBuild(adapter, module);

        configureRunParameters(root, buildTool, run);

        bundleRun.configure(task -> task.dependsOn(run.steps()));
        bundleBuild.configure(task -> task.dependsOn(build.steps()));
    }

    private void configureGeneratedHost(
            Project root,
            ModuleComposerExtension extension,
            GradleBuildToolAdapter buildTool,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            TaskProvider<Task> bundleRun,
            TaskProvider<Task> bundleBuild
    ) {
        List<String> jarTasks = new ArrayList<>();
        List<Provider<String>> jarPaths = new ArrayList<>();

        for (String projectPath : extension.getCommonProjectPaths().get()) {
            BuildInvocation artifact = buildTool.projectArtifact(projectPath);
            jarTasks.addAll(artifact.steps());
            jarPaths.add(buildTool.archiveFilePath(artifact.steps().get(0)));
        }

        for (ModuleRegistration module : plan.modules()) {
            BuildInvocation artifact = buildTool.moduleArtifact(module);
            jarTasks.addAll(artifact.steps());
            jarPaths.add(buildTool.archiveFilePath(artifact.steps().get(0)));
        }

        List<String> configurationClasses = new ArrayList<>(
                extension.getCommonConfigurationClasses().get()
        );

        configurationClasses.addAll(
                plan.modules()
                        .stream()
                        .map(ModuleRegistration::configurationClass)
                        .toList()
        );

        TaskProvider<GenerateHostTask> prepare =
                root.getTasks().register(
                        "prepareGeneratedHost",
                        GenerateHostTask.class,
                        task -> {
                            task.setGroup("module composer");
                            task.dependsOn(jarTasks);
                            task.setAdapter(adapter);
                            task.setPlan(plan);
                            task.getHostDirectory().set(
                                    extension.getGeneratedHostDirectory()
                            );
                            jarPaths.forEach(task.getDependencyJarPaths()::add);
                            task.getConfigurationClasses().set(configurationClasses);
                            task.getModuleNames().set(plan.moduleNames());
                            task.getDistribution().set(
                                    plan.distribution() == null
                                            ? ""
                                            : plan.distribution()
                            );
                            task.getJavaVersion().set(extension.getJavaVersion());
                            task.getFrameworkOptions().put(
                                    "springBootVersion",
                                    extension.getSpringBootVersion()
                            );
                            task.getFrameworkOptions().put(
                                    "dependencyManagementVersion",
                                    extension.getDependencyManagementVersion()
                            );
                        }
                );

        TaskProvider<Exec> runGenerated =
                root.getTasks().register(
                        "runGeneratedHost",
                        Exec.class,
                        task -> {
                            task.setGroup("module composer");
                            task.dependsOn(prepare);
                            configureGeneratedGradleInvocation(
                                    root,
                                    extension,
                                    task,
                                    buildTool.generatedRun(adapter).steps(),
                                    true
                            );
                        }
                );

        TaskProvider<Exec> buildGenerated =
                root.getTasks().register(
                        "buildGeneratedHost",
                        Exec.class,
                        task -> {
                            task.setGroup("module composer");
                            task.dependsOn(prepare);
                            configureGeneratedGradleInvocation(
                                    root,
                                    extension,
                                    task,
                                    buildTool.generatedBuild(adapter).steps(),
                                    false
                            );
                        }
                );

        TaskProvider<Task> copyJar = root.getTasks().register(
                "copyGeneratedHostJar",
                task -> {
                    task.setGroup("module composer");
                    task.dependsOn(buildGenerated);

                    task.doLast(ignored -> {
                        File source = extension
                                .getGeneratedHostDirectory()
                                .get()
                                .getAsFile()
                                .toPath()
                                .resolve(adapter.generatedArtifact())
                                .toFile();

                        File target = extension
                                .getOutputJar()
                                .get()
                                .getAsFile();

                        if (!source.exists()) {
                            throw new GradleException(
                                    "Generated JAR not found: " +
                                            source.getAbsolutePath()
                            );
                        }

                        target.getParentFile().mkdirs();

                        root.copy(spec -> {
                            spec.from(source);
                            spec.into(target.getParentFile());
                            spec.rename(
                                    ignoredName -> target.getName()
                            );
                        });

                        root.getLogger().lifecycle(
                                "Combined JAR: {}",
                                target.getAbsolutePath()
                        );
                    });
                }
        );

        bundleRun.configure(task -> task.dependsOn(runGenerated));
        bundleBuild.configure(task -> task.dependsOn(copyJar));
    }

    private void configureRunParameters(
            Project root,
            GradleBuildToolAdapter buildTool,
            BuildInvocation invocation
    ) {
        String taskPath = invocation.steps().get(0);
        Project targetProject = buildTool.projectForTaskPath(taskPath);
        String taskName = buildTool.taskName(taskPath);

        targetProject.getTasks().named(taskName).configure(task -> {
            if (task instanceof JavaExec javaExec) {
                List<String> arguments = runArguments(root);
                if (!arguments.isEmpty()) {
                    javaExec.args(arguments);
                }
            }
        });
    }

    private void configureGeneratedGradleInvocation(
            Project root,
            ModuleComposerExtension extension,
            Exec task,
            List<String> tasks,
            boolean includeRunParameters
    ) {
        File hostDirectory = extension.getGeneratedHostDirectory()
                .get()
                .getAsFile();

        task.setWorkingDir(hostDirectory);

        List<String> command = new ArrayList<>();
        command.add(gradleExecutable(root));
        command.addAll(tasks);

        if (includeRunParameters) {
            Integer port = resolvePort(root);
            if (port != null) {
                command.add("-Pport=" + port);
            }
        }

        task.commandLine(command);
    }

    private String gradleExecutable(Project root) {
        String executable = isWindows() ? "gradlew.bat" : "gradlew";
        File wrapper = root.file(executable);
        if (wrapper.exists()) {
            return wrapper.getAbsolutePath();
        }
        return isWindows() ? "gradle.bat" : "gradle";
    }

    private static boolean isWindows() {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("windows");
    }

    private static boolean selectionTaskRequested(Project root) {
        return root.getGradle()
                .getStartParameter()
                .getTaskNames()
                .stream()
                .map(ModuleComposerPlugin::simpleTaskName)
                .anyMatch(name ->
                        name.equals("bundleRun")
                                || name.equals("bundleBuild")
                                || name.equals("explain")
                );
    }

    private static String simpleTaskName(String taskName) {
        int index = taskName.lastIndexOf(':');
        return index < 0 ? taskName : taskName.substring(index + 1);
    }

    private static SelectionRequest selectionRequest(Project root) {
        return new SelectionRequest(
                parseList(root, "modules"),
                stringProperty(root, "distribution"),
                parseList(root, "includeModules"),
                parseList(root, "excludeModules"),
                runtimeOptions(root)
        );
    }

    private static RuntimeOptions runtimeOptions(Project root) {
        return new RuntimeOptions(
                resolvePort(root),
                stringProperty(root, "profile"),
                booleanProperty(root, "debug"),
                List.of(),
                Map.of()
        );
    }

    private static Integer resolvePort(Project root) {
        String value = stringProperty(root, "port");
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            int port = Integer.parseInt(value.trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException exception) {
            throw new GradleException(
                    "Invalid port '" + value +
                            "'. -Pport must be an integer between 1 and 65535."
            );
        }
    }

    private static List<String> parseList(Project root, String propertyName) {
        String raw = stringProperty(root, propertyName);
        if (raw == null) {
            return List.of();
        }
        return List.of(raw.split(","));
    }

    private static String stringProperty(Project root, String propertyName) {
        Object raw = root.findProperty(propertyName);
        return raw == null ? null : String.valueOf(raw).trim();
    }

    private static boolean booleanProperty(Project root, String propertyName) {
        String value = stringProperty(root, propertyName);
        return value != null && Boolean.parseBoolean(value);
    }

    private static List<String> runArguments(Project root) {
        Integer port = resolvePort(root);
        if (port == null) {
            return List.of();
        }
        return List.of("--server.port=" + port);
    }

    private static Path distributionPath(
            Project project,
            ModuleComposerExtension extension
    ) {
        return project.file(extension.getDistributionFile().get()).toPath();
    }

    private void explainPlan(
            Project project,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            GradleBuildToolAdapter buildTool,
            CompositionPlan plan
    ) {
        project.getLogger().lifecycle("");
        project.getLogger().lifecycle("Module Composer");
        project.getLogger().lifecycle("---------------");
        project.getLogger().lifecycle("Framework      : {}", plan.framework());
        project.getLogger().lifecycle("Build tool     : {}", buildTool.buildToolId());
        project.getLogger().lifecycle("Execution      : {}", plan.executionMode());
        project.getLogger().lifecycle("Adapter        : {}", adapter.getClass().getSimpleName());
        project.getLogger().lifecycle("Selection mode : {}", plan.selectionMode());
        if (plan.distribution() != null) {
            project.getLogger().lifecycle("Distribution   : {}", plan.distribution());
        }
        project.getLogger().lifecycle(
                "Port           : {}",
                plan.runtimeOptions().port() == null
                        ? "default"
                        : plan.runtimeOptions().port()
        );
        project.getLogger().lifecycle("Modules:");
        plan.moduleNames().forEach(
                name -> project.getLogger().lifecycle("  + {}", name)
        );

        if (plan.isStandalone()) {
            ModuleRegistration module = plan.modules().get(0);
            project.getLogger().lifecycle("Run task:");
            project.getLogger().lifecycle("  {}", buildTool.standaloneRun(adapter, module).displayName());
            project.getLogger().lifecycle("Build task:");
            project.getLogger().lifecycle("  {}", buildTool.standaloneBuild(adapter, module).displayName());
        } else {
            project.getLogger().lifecycle("Generated Host:");
            project.getLogger().lifecycle(
                    "  {}",
                    extension.getGeneratedHostDirectory()
                            .get()
                            .getAsFile()
                            .getPath()
            );
            project.getLogger().lifecycle("Build output:");
            project.getLogger().lifecycle(
                    "  {}",
                    extension.getOutputJar()
                            .get()
                            .getAsFile()
                            .getPath()
            );
        }

        project.getLogger().lifecycle("");
    }
}
