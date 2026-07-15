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
import java.io.IOException;
import java.nio.file.Files;
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
                config.distributions().forEach((name, preset) -> {
                    if (preset.applicationName() == null) {
                        project.getLogger().lifecycle("  + {}", name);
                    } else {
                        project.getLogger().lifecycle(
                                "  + {} (application: {})",
                                name,
                                preset.applicationName()
                        );
                    }
                    if (preset.artifact() != null
                            && preset.artifact().fileName() != null) {
                        project.getLogger().lifecycle(
                                "      artifact: {}",
                                preset.artifact().fileName()
                        );
                    }
                    if (preset.container() != null) {
                        int hostPort = containerHostPort(preset.container());
                        int containerPort = containerContainerPort(preset.container());
                        project.getLogger().lifecycle(
                                "      container: {}:{}->{}",
                                preset.container().image() == null
                                        ? "(no image)"
                                        : preset.container().image(),
                                hostPort,
                                containerPort
                        );
                    }
                });
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
        List<String> validationTasks = validationTasks(root, plan);

        configureRunParameters(root, buildTool, run);

        bundleRun.configure(task -> {
            task.dependsOn(validationTasks);
            task.dependsOn(run.steps());
        });
        bundleBuild.configure(task -> {
            task.dependsOn(validationTasks);
            task.dependsOn(build.steps());
        });
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
        List<String> validationTasks = validationTasks(root, plan);

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

        File hostDirectory = generatedHostDirectory(extension, plan);

        TaskProvider<GenerateHostTask> prepare =
                root.getTasks().register(
                        "prepareGeneratedHost",
                        GenerateHostTask.class,
                        task -> {
	                            task.setGroup("module composer");
	                            task.dependsOn(validationTasks);
	                            task.dependsOn(jarTasks);
                            task.setAdapter(adapter);
                            task.setPlan(plan);
                            task.getHostDirectory().set(hostDirectory);
                            jarPaths.forEach(task.getDependencyJarPaths()::add);
                            task.getConfigurationClasses().set(configurationClasses);
                            task.getModuleNames().set(plan.moduleNames());
                            task.getDistribution().set(
                                    plan.distribution() == null
                                            ? ""
                                            : plan.distribution()
                            );
                            task.getApplicationName().set(plan.applicationName());
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
                                    hostDirectory,
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
                                    hostDirectory,
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
                        File source = hostDirectory
                                .toPath()
                                .resolve(adapter.generatedArtifact())
                                .toFile();

                        File target = outputJar(extension, plan);

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

                        writeContainerFiles(plan, target);

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

    private List<String> validationTasks(Project root, CompositionPlan plan) {
        String validation = validationMode(root);
        if (validation.equals("none")) {
            return List.of();
        }

        return plan.modules()
                .stream()
                .map(module -> module.projectPath() + ":" + validation)
                .toList();
    }

    private static String validationMode(Project root) {
        String value = stringProperty(root, "validation");
        if (value == null || value.isBlank()) {
            return "none";
        }

        String normalized = value.trim().toLowerCase();
        if (normalized.equals("none")
                || normalized.equals("test")
                || normalized.equals("check")) {
            return normalized;
        }

        throw new GradleException(
                "Invalid validation '" + value +
                        "'. -Pvalidation must be one of: none, test, check."
        );
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
            File hostDirectory,
            Exec task,
            List<String> tasks,
            boolean includeRunParameters
    ) {
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
                stringProperty(root, "applicationName"),
                parseList(root, "includeModules"),
                parseList(root, "excludeModules"),
                runtimeOptions(root)
        );
    }

    private void writeContainerFiles(CompositionPlan plan, File outputJar) {
        File directory = containerOutputDirectory(plan, outputJar);
        if (plan.container() == null) {
            deleteContainerFiles(directory);
            return;
        }

        int hostPort = containerHostPort(plan.container());
        int containerPort = containerContainerPort(plan.container());
        String image = plan.container().image() == null
                ? plan.applicationName() + ":local"
                : plan.container().image();
        String baseImage = plan.container().baseImage() == null
                ? "eclipse-temurin:21-jre"
                : plan.container().baseImage();
        String serviceName = containerServiceName(plan.applicationName());
        String jarReference = outputJar.getName();

        try {
            Files.createDirectories(directory.toPath());
            Files.writeString(
                    directory.toPath().resolve("Dockerfile"),
                    """
                    FROM %s

                    WORKDIR /app

                    ARG JAR_FILE=%s
                    COPY ${JAR_FILE} /app/app.jar

                    EXPOSE %d

                    ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                    """.formatted(baseImage, jarReference, containerPort)
            );

            Files.writeString(
                    directory.toPath().resolve("docker-compose.yml"),
                    (
                            "services:\n" +
                                    "  %s:\n" +
                                    "    build:\n" +
                                    "      context: ../..\n" +
                                    "      dockerfile: containers/%s/Dockerfile\n" +
                                    "      args:\n" +
                                    "        JAR_FILE: %s\n" +
                                    "    image: %s\n" +
                                    "    ports:\n" +
                                    "      - \"%d:%d\"\n" +
                                    "    restart: unless-stopped\n"
                    ).formatted(
                            serviceName,
                            serviceName,
                            jarReference,
                            image,
                            hostPort,
                            containerPort
                    )
            );
        } catch (IOException exception) {
            throw new GradleException(
                    "Unable to write container files to " +
                            directory.getAbsolutePath(),
                    exception
            );
        }
    }

    private void deleteContainerFiles(File directory) {
        try {
            Files.deleteIfExists(directory.toPath().resolve("Dockerfile"));
            Files.deleteIfExists(directory.toPath().resolve("docker-compose.yml"));
            Files.deleteIfExists(directory.toPath());
        } catch (IOException exception) {
            throw new GradleException(
                    "Unable to remove stale container files from " +
                            directory.getAbsolutePath(),
                    exception
            );
        }
    }

    private static File containerOutputDirectory(
            CompositionPlan plan,
            File outputJar
    ) {
        return outputJar.getParentFile()
                .toPath()
                .resolve("containers")
                .resolve(containerServiceName(plan.applicationName()))
                .toFile();
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
        project.getLogger().lifecycle("Application    : {}", plan.applicationName());
        project.getLogger().lifecycle("Validation     : {}", validationMode(project));
        if (plan.artifact() != null && plan.artifact().fileName() != null) {
            project.getLogger().lifecycle(
                    "Artifact       : {}",
                    plan.artifact().fileName()
            );
        }
        if (plan.container() != null) {
            int hostPort = containerHostPort(plan.container());
            int containerPort = containerContainerPort(plan.container());
            project.getLogger().lifecycle(
                    "Container      : {}:{}->{}",
                    plan.container().image() == null
                            ? "(no image)"
                            : plan.container().image(),
                    hostPort,
                    containerPort
            );
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
            File host = generatedHostDirectory(extension, plan);
            File output = outputJar(extension, plan);
            project.getLogger().lifecycle("Generated Host:");
            project.getLogger().lifecycle(
                    "  {}",
                    host.getPath()
            );
            project.getLogger().lifecycle("Build output:");
            project.getLogger().lifecycle(
                    "  {}",
                    output.getPath()
            );
        }

        project.getLogger().lifecycle("");
    }

    private static File outputJar(
            ModuleComposerExtension extension,
            CompositionPlan plan
    ) {
        File configured = extension.getOutputJar().get().getAsFile();
        if (!configured.getName().equals("combined-app.jar")) {
            return configured;
        }
        if (plan.artifact() != null && plan.artifact().fileName() != null) {
            return configured.toPath()
                    .resolveSibling(plan.artifact().fileName())
                    .toFile();
        }
        return configured.toPath()
                .resolveSibling(plan.applicationName() + ".jar")
                .toFile();
    }

    private static File generatedHostDirectory(
            ModuleComposerExtension extension,
            CompositionPlan plan
    ) {
        File configured = extension.getGeneratedHostDirectory()
                .get()
                .getAsFile();
        Path configuredPath = configured.toPath();
        Path parent = configuredPath.getParent();

        if (!configured.getName().equals("combined-app") || parent == null) {
            return configured;
        }

        if (parent.getFileName() != null
                && parent.getFileName().toString().equals("generated")) {
            return parent.resolve(plan.applicationName()).toFile();
        }

        if (parent.getFileName() != null
                && parent.getFileName().toString().equals("module-composer")) {
            return parent.resolve("generated")
                    .resolve(plan.applicationName())
                    .toFile();
        }

        return configured;
    }

    private static String containerServiceName(String value) {
        String normalized = value
                .toLowerCase()
                .replaceAll("[^a-z0-9_-]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        return normalized.isBlank() ? "app" : normalized;
    }

    private static int containerHostPort(DistributionContainer container) {
        if (container.hostPort() != null) {
            return container.hostPort();
        }
        if (container.containerPort() != null) {
            return container.containerPort();
        }
        return 8080;
    }

    private static int containerContainerPort(DistributionContainer container) {
        if (container.containerPort() != null) {
            return container.containerPort();
        }
        if (container.hostPort() != null) {
            return container.hostPort();
        }
        return 8080;
    }
}
