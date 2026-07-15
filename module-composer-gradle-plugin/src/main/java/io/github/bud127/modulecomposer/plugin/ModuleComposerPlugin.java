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

    private static final String TASK_GROUP = "module composer";
    private static final String BUNDLE_RUN_TASK = "bundleRun";
    private static final String BUNDLE_BUILD_TASK = "bundleBuild";
    private static final String EXPLAIN_TASK = "explain";
    private static final String LIST_MODULES_TASK = "listModules";
    private static final String LIST_DISTRIBUTIONS_TASK = "listDistributions";
    private static final String PREPARE_GENERATED_HOST_TASK = "prepareGeneratedHost";
    private static final String RUN_GENERATED_HOST_TASK = "runGeneratedHost";
    private static final String BUILD_GENERATED_HOST_TASK = "buildGeneratedHost";
    private static final String COPY_GENERATED_HOST_JAR_TASK = "copyGeneratedHostJar";
    private static final String APPLICATION_NAME_PROPERTY = "applicationName";
    private static final String DISTRIBUTION_PROPERTY = "distribution";
    private static final String MODULES_PROPERTY = "modules";
    private static final String INCLUDE_MODULES_PROPERTY = "includeModules";
    private static final String EXCLUDE_MODULES_PROPERTY = "excludeModules";
    private static final String PROFILE_PROPERTY = "profile";
    private static final String DEBUG_PROPERTY = "debug";
    private static final String VALIDATION_PROPERTY = "validation";
    private static final String GENERATED_DIRECTORY_NAME = "generated";
    private static final String MODULE_COMPOSER_DIRECTORY_NAME = "module-composer";
    private static final String ITEM_LOG_FORMAT = "  + {}";
    private static final String DEFAULT_DISTRIBUTION_FILE = "distributions.yml";

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
        configureExtensionDefaults(project, extension);

        TaskProvider<Task> bundleRun = registerTask(
                project,
                BUNDLE_RUN_TASK,
                "Runs one module directly or a generated combined host."
        );

        TaskProvider<Task> bundleBuild = registerTask(
                project,
                BUNDLE_BUILD_TASK,
                "Builds one module directly or a generated combined JAR."
        );

        TaskProvider<Task> explain = registerTask(
                project,
                EXPLAIN_TASK,
                "Explains the selected execution and build plan."
        );

        registerDiscoveryTasks(project, extension, registry);

        project.getGradle().projectsEvaluated(gradle -> {
            if (!selectionTaskRequested(project)) {
                return;
            }

            FrameworkAdapter adapter = adapterRegistry()
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

    private static void configureExtensionDefaults(
            Project project,
            ModuleComposerExtension extension
    ) {
        extension.getDistributionFile().convention(DEFAULT_DISTRIBUTION_FILE);
        extension.getFramework().convention(ModuleComposerDefaults.DEFAULT_FRAMEWORK_ID);
        extension.getGeneratedHostDirectory().convention(
                project.getLayout().getBuildDirectory().dir(
                        "module-composer/generated/" +
                                ModuleComposerDefaults.DEFAULT_APPLICATION_NAME
                )
        );
        extension.getOutputJar().convention(
                project.getLayout().getBuildDirectory().file(
                        "module-composer/output/" +
                                ModuleComposerDefaults.DEFAULT_APPLICATION_NAME +
                                ".jar"
                )
        );
        extension.getSpringBootVersion().convention(
                ModuleComposerDefaults.SPRING_BOOT_VERSION
        );
        extension.getDependencyManagementVersion().convention(
                ModuleComposerDefaults.DEPENDENCY_MANAGEMENT_VERSION
        );
        extension.getJavaVersion().convention(ModuleComposerDefaults.JAVA_VERSION);
        extension.getCommonProjectPaths().convention(List.of());
        extension.getCommonConfigurationClasses().convention(List.of());
    }

    private FrameworkAdapterRegistry adapterRegistry() {
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
            task.setGroup(TASK_GROUP);
            task.setDescription(description);
        });
    }

    private void registerDiscoveryTasks(
            Project project,
            ModuleComposerExtension extension,
            ModuleRegistry registry
    ) {
        registerListModulesTask(project, registry);
        registerListDistributionsTask(project, extension);
    }

    private void registerListModulesTask(
            Project project,
            ModuleRegistry registry
    ) {
        project.getTasks().register(LIST_MODULES_TASK, task -> {
            task.setGroup(TASK_GROUP);
            task.doLast(ignored -> {
                project.getLogger().lifecycle("Available modules:");
                if (registry.all().isEmpty()) {
                    project.getLogger().lifecycle("  (none registered)");
                    return;
                }

                registry.all().forEach(module -> logModule(project, module));
            });
        });
    }

    private void registerListDistributionsTask(
            Project project,
            ModuleComposerExtension extension
    ) {
        project.getTasks().register(LIST_DISTRIBUTIONS_TASK, task -> {
            task.setGroup(TASK_GROUP);
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
                config.distributions().forEach(
                        (name, preset) -> logDistribution(project, name, preset)
                );
            });
        });
    }

    private static void logModule(Project project, ModuleRegistration module) {
        project.getLogger().lifecycle(ITEM_LOG_FORMAT, module.name());
        project.getLogger().lifecycle(
                "      project: {}",
                module.projectPath()
        );
        project.getLogger().lifecycle(
                "      configuration: {}",
                module.configurationClass()
        );
    }

    private static void logDistribution(
            Project project,
            String name,
            DistributionPreset preset
    ) {
        logDistributionHeader(project, name, preset);
        logDistributionArtifact(project, preset);
        logDistributionContainer(project, preset);
    }

    private static void logDistributionHeader(
            Project project,
            String name,
            DistributionPreset preset
    ) {
        if (preset.applicationName() == null) {
            project.getLogger().lifecycle(ITEM_LOG_FORMAT, name);
            return;
        }

        project.getLogger().lifecycle(
                "  + {} (application: {})",
                name,
                preset.applicationName()
        );
    }

    private static void logDistributionArtifact(
            Project project,
            DistributionPreset preset
    ) {
        if (preset.artifact() == null || preset.artifact().fileName() == null) {
            return;
        }

        project.getLogger().lifecycle(
                "      artifact: {}",
                preset.artifact().fileName()
        );
    }

    private static void logDistributionContainer(
            Project project,
            DistributionPreset preset
    ) {
        if (preset.container() == null) {
            return;
        }

        int hostPort = containerHostPort(preset.container());
        int containerPort = containerContainerPort(preset.container());
        project.getLogger().lifecycle(
                "      container: {}:{}->{}",
                containerImage(preset.container()),
                hostPort,
                containerPort
        );
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
        List<String> validationTasks = validationTasks(root, plan);
        GeneratedHostInputs inputs = generatedHostInputs(
                extension,
                buildTool,
                plan
        );
        File hostDirectory = generatedHostDirectory(extension, plan);

        TaskProvider<GenerateHostTask> prepare = registerPrepareGeneratedHost(
                root,
                extension,
                adapter,
                plan,
                validationTasks,
                inputs,
                hostDirectory
        );
        TaskProvider<Exec> runGenerated = registerGeneratedExec(
                root,
                buildTool,
                adapter,
                hostDirectory,
                prepare,
                true
        );
        TaskProvider<Exec> buildGenerated = registerGeneratedExec(
                root,
                buildTool,
                adapter,
                hostDirectory,
                prepare,
                false
        );
        TaskProvider<Task> copyJar = registerCopyGeneratedHostJar(
                root,
                extension,
                adapter,
                plan,
                hostDirectory,
                buildGenerated
        );

        bundleRun.configure(task -> task.dependsOn(runGenerated));
        bundleBuild.configure(task -> task.dependsOn(copyJar));
    }

    private GeneratedHostInputs generatedHostInputs(
            ModuleComposerExtension extension,
            GradleBuildToolAdapter buildTool,
            CompositionPlan plan
    ) {
        List<String> jarTasks = new ArrayList<>();
        List<Provider<String>> jarPaths = new ArrayList<>();
        collectCommonArtifacts(extension, buildTool, jarTasks, jarPaths);
        collectModuleArtifacts(plan, buildTool, jarTasks, jarPaths);
        return new GeneratedHostInputs(
                jarTasks,
                jarPaths,
                configurationClasses(extension, plan)
        );
    }

    private static void collectCommonArtifacts(
            ModuleComposerExtension extension,
            GradleBuildToolAdapter buildTool,
            List<String> jarTasks,
            List<Provider<String>> jarPaths
    ) {
        for (String projectPath : extension.getCommonProjectPaths().get()) {
            addArtifact(buildTool.projectArtifact(projectPath), buildTool, jarTasks, jarPaths);
        }
    }

    private static void collectModuleArtifacts(
            CompositionPlan plan,
            GradleBuildToolAdapter buildTool,
            List<String> jarTasks,
            List<Provider<String>> jarPaths
    ) {
        for (ModuleRegistration module : plan.modules()) {
            addArtifact(buildTool.moduleArtifact(module), buildTool, jarTasks, jarPaths);
        }
    }

    private static void addArtifact(
            BuildInvocation artifact,
            GradleBuildToolAdapter buildTool,
            List<String> jarTasks,
            List<Provider<String>> jarPaths
    ) {
        jarTasks.addAll(artifact.steps());
        jarPaths.add(buildTool.archiveFilePath(artifact.steps().get(0)));
    }

    private static List<String> configurationClasses(
            ModuleComposerExtension extension,
            CompositionPlan plan
    ) {
        List<String> configurationClasses = new ArrayList<>(
                extension.getCommonConfigurationClasses().get()
        );
        configurationClasses.addAll(
                plan.modules()
                        .stream()
                        .map(ModuleRegistration::configurationClass)
                        .toList()
        );
        return configurationClasses;
    }

    private TaskProvider<GenerateHostTask> registerPrepareGeneratedHost(
            Project root,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            List<String> validationTasks,
            GeneratedHostInputs inputs,
            File hostDirectory
    ) {
        return root.getTasks().register(
                PREPARE_GENERATED_HOST_TASK,
                GenerateHostTask.class,
                task -> configurePrepareGeneratedHost(
                        task,
                        extension,
                        adapter,
                        plan,
                        validationTasks,
                        inputs,
                        hostDirectory
                )
        );
    }

    private static void configurePrepareGeneratedHost(
            GenerateHostTask task,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            List<String> validationTasks,
            GeneratedHostInputs inputs,
            File hostDirectory
    ) {
        task.setGroup(TASK_GROUP);
        task.dependsOn(validationTasks);
        task.dependsOn(inputs.jarTasks());
        task.setAdapter(adapter);
        task.setPlan(plan);
        task.getHostDirectory().set(hostDirectory);
        inputs.jarPaths().forEach(task.getDependencyJarPaths()::add);
        task.getConfigurationClasses().set(inputs.configurationClasses());
        task.getModuleNames().set(plan.moduleNames());
        task.getDistribution().set(plan.distribution() == null ? "" : plan.distribution());
        task.getApplicationName().set(plan.applicationName());
        task.getJavaVersion().set(extension.getJavaVersion());
        task.getFrameworkOptions().put(
                ModuleComposerDefaults.SPRING_BOOT_VERSION_KEY,
                extension.getSpringBootVersion()
        );
        task.getFrameworkOptions().put(
                ModuleComposerDefaults.DEPENDENCY_MANAGEMENT_VERSION_KEY,
                extension.getDependencyManagementVersion()
        );
    }

    private TaskProvider<Exec> registerGeneratedExec(
            Project root,
            GradleBuildToolAdapter buildTool,
            FrameworkAdapter adapter,
            File hostDirectory,
            TaskProvider<GenerateHostTask> prepare,
            boolean runMode
    ) {
        String taskName = runMode
                ? RUN_GENERATED_HOST_TASK
                : BUILD_GENERATED_HOST_TASK;
        BuildInvocation invocation = runMode
                ? buildTool.generatedRun(adapter)
                : buildTool.generatedBuild(adapter);
        return root.getTasks().register(
                taskName,
                Exec.class,
                task -> {
                    task.setGroup(TASK_GROUP);
                    task.dependsOn(prepare);
                    configureGeneratedGradleInvocation(
                            root,
                            hostDirectory,
                            task,
                            invocation.steps(),
                            runMode
                    );
                }
        );
    }

    private TaskProvider<Task> registerCopyGeneratedHostJar(
            Project root,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            File hostDirectory,
            TaskProvider<Exec> buildGenerated
    ) {
        return root.getTasks().register(
                COPY_GENERATED_HOST_JAR_TASK,
                task -> {
                    task.setGroup(TASK_GROUP);
                    task.dependsOn(buildGenerated);
                    task.doLast(ignored -> copyGeneratedHostJar(
                            root,
                            extension,
                            adapter,
                            plan,
                            hostDirectory
                    ));
                }
        );
    }

    private void copyGeneratedHostJar(
            Project root,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            File hostDirectory
    ) {
        File source = hostDirectory
                .toPath()
                .resolve(adapter.generatedArtifact())
                .toFile();
        File target = outputJar(extension, plan);

        if (!source.exists()) {
            throw new GradleException(
                    "Generated JAR not found: " + source.getAbsolutePath()
            );
        }

        target.getParentFile().mkdirs();
        root.copy(spec -> {
            spec.from(source);
            spec.into(target.getParentFile());
            spec.rename(ignoredName -> target.getName());
        });

        writeContainerFiles(plan, target);
        root.getLogger().lifecycle("Combined JAR: {}", target.getAbsolutePath());
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
        String value = stringProperty(root, VALIDATION_PROPERTY);
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
                        name.equals(BUNDLE_RUN_TASK)
                                || name.equals(BUNDLE_BUILD_TASK)
                                || name.equals(EXPLAIN_TASK)
                );
    }

    private static String simpleTaskName(String taskName) {
        int index = taskName.lastIndexOf(':');
        return index < 0 ? taskName : taskName.substring(index + 1);
    }

    private static SelectionRequest selectionRequest(Project root) {
        return new SelectionRequest(
                parseList(root, MODULES_PROPERTY),
                stringProperty(root, DISTRIBUTION_PROPERTY),
                stringProperty(root, APPLICATION_NAME_PROPERTY),
                parseList(root, INCLUDE_MODULES_PROPERTY),
                parseList(root, EXCLUDE_MODULES_PROPERTY),
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
                    """
                    services:
                      %s:
                        build:
                          context: ../..
                          dockerfile: containers/%s/Dockerfile
                          args:
                            JAR_FILE: %s
                        image: %s
                        ports:
                          - "%d:%d"
                        restart: unless-stopped
                    """.formatted(
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
                stringProperty(root, PROFILE_PROPERTY),
                booleanProperty(root, DEBUG_PROPERTY),
                List.of(),
                Map.of()
        );
    }

    private static Integer resolvePort(Project root) {
        String value = stringProperty(root, ModuleComposerDefaults.RUNTIME_PORT_PROPERTY);
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
        return commaSeparatedValues(raw);
    }

    private static List<String> commaSeparatedValues(String raw) {
        List<String> values = new ArrayList<>();
        int start = 0;
        for (int index = 0; index < raw.length(); index++) {
            if (raw.charAt(index) == ',') {
                values.add(raw.substring(start, index));
                start = index + 1;
            }
        }
        values.add(raw.substring(start));
        return values;
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
        logPlanSummary(project, adapter, buildTool, plan);
        logPlanModules(project, plan);
        logPlanExecution(project, extension, adapter, buildTool, plan);
        project.getLogger().lifecycle("");
    }

    private void logPlanSummary(
            Project project,
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
        logPlanDistribution(project, plan);
        project.getLogger().lifecycle("Application    : {}", plan.applicationName());
        project.getLogger().lifecycle("Validation     : {}", validationMode(project));
        logPlanArtifact(project, plan);
        logPlanContainer(project, plan);
        project.getLogger().lifecycle("Port           : {}", runtimePort(plan));
    }

    private static void logPlanDistribution(
            Project project,
            CompositionPlan plan
    ) {
        if (plan.distribution() != null) {
            project.getLogger().lifecycle("Distribution   : {}", plan.distribution());
        }
    }

    private static void logPlanArtifact(Project project, CompositionPlan plan) {
        if (plan.artifact() == null || plan.artifact().fileName() == null) {
            return;
        }

        project.getLogger().lifecycle(
                "Artifact       : {}",
                plan.artifact().fileName()
        );
    }

    private static void logPlanContainer(Project project, CompositionPlan plan) {
        if (plan.container() == null) {
            return;
        }

        int hostPort = containerHostPort(plan.container());
        int containerPort = containerContainerPort(plan.container());
        project.getLogger().lifecycle(
                "Container      : {}:{}->{}",
                containerImage(plan.container()),
                hostPort,
                containerPort
        );
    }

    private static Object runtimePort(CompositionPlan plan) {
        return plan.runtimeOptions().port() == null
                ? "default"
                : plan.runtimeOptions().port();
    }

    private static void logPlanModules(Project project, CompositionPlan plan) {
        project.getLogger().lifecycle("Modules:");
        plan.moduleNames().forEach(
                name -> project.getLogger().lifecycle(ITEM_LOG_FORMAT, name)
        );
    }

    private void logPlanExecution(
            Project project,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            GradleBuildToolAdapter buildTool,
            CompositionPlan plan
    ) {
        if (plan.isStandalone()) {
            logStandalonePlan(project, adapter, buildTool, plan.modules().get(0));
            return;
        }

        logGeneratedPlan(project, extension, plan);
    }

    private static void logStandalonePlan(
            Project project,
            FrameworkAdapter adapter,
            GradleBuildToolAdapter buildTool,
            ModuleRegistration module
    ) {
        project.getLogger().lifecycle("Run task:");
        project.getLogger().lifecycle(
                "  {}",
                buildTool.standaloneRun(adapter, module).displayName()
        );
        project.getLogger().lifecycle("Build task:");
        project.getLogger().lifecycle(
                "  {}",
                buildTool.standaloneBuild(adapter, module).displayName()
        );
    }

    private static void logGeneratedPlan(
            Project project,
            ModuleComposerExtension extension,
            CompositionPlan plan
    ) {
        File host = generatedHostDirectory(extension, plan);
        File output = outputJar(extension, plan);
        project.getLogger().lifecycle("Generated Host:");
        project.getLogger().lifecycle("  {}", host.getPath());
        project.getLogger().lifecycle("Build output:");
        project.getLogger().lifecycle("  {}", output.getPath());
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

        if (!configured.getName().equals(ModuleComposerDefaults.DEFAULT_APPLICATION_NAME)
                || parent == null) {
            return configured;
        }

        if (parent.getFileName() != null
                && parent.getFileName().toString().equals(GENERATED_DIRECTORY_NAME)) {
            return parent.resolve(plan.applicationName()).toFile();
        }

        if (parent.getFileName() != null
                && parent.getFileName().toString().equals(MODULE_COMPOSER_DIRECTORY_NAME)) {
            return parent.resolve(GENERATED_DIRECTORY_NAME)
                    .resolve(plan.applicationName())
                    .toFile();
        }

        return configured;
    }

    private static String containerServiceName(String value) {
        StringBuilder normalized = new StringBuilder();
        boolean pendingSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if (isContainerServiceNameCharacter(character)) {
                appendPendingSeparator(normalized, pendingSeparator);
                normalized.append(character);
                pendingSeparator = false;
            } else {
                pendingSeparator = !normalized.isEmpty();
            }
        }
        return normalized.isEmpty() ? "app" : normalized.toString();
    }

    private static void appendPendingSeparator(
            StringBuilder value,
            boolean pendingSeparator
    ) {
        if (pendingSeparator) {
            value.append('-');
        }
    }

    private static boolean isContainerServiceNameCharacter(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '_'
                || value == '-';
    }

    private static String containerImage(DistributionContainer container) {
        return container.image() == null ? "(no image)" : container.image();
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

    private record GeneratedHostInputs(
            List<String> jarTasks,
            List<Provider<String>> jarPaths,
            List<String> configurationClasses
    ) {
    }
}
