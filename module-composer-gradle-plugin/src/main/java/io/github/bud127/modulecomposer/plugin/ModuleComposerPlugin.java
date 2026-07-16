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

    private static final String TASK_GROUP = "module composer";
    private static final String BUNDLE_RUN_TASK = "bundleRun";
    private static final String BUNDLE_BUILD_TASK = "bundleBuild";
    private static final String BUNDLE_TEST_TASK = "bundleTest";
    private static final String EXPLAIN_TASK = "explain";
    private static final String LIST_MODULES_TASK = "listModules";
    private static final String LIST_DISTRIBUTIONS_TASK = "listDistributions";
    private static final String PREPARE_GENERATED_HOST_TASK = "prepareGeneratedHost";
    private static final String RUN_GENERATED_HOST_TASK = "runGeneratedHost";
    private static final String BUILD_GENERATED_HOST_TASK = "buildGeneratedHost";
    private static final String TEST_GENERATED_HOST_TASK = "testGeneratedHost";
    private static final String COPY_GENERATED_HOST_JAR_TASK = "copyGeneratedHostJar";
    private static final String APPLICATION_NAME_PROPERTY =
            ModuleComposerDefaults.APPLICATION_NAME_KEY;
    private static final String DISTRIBUTION_PROPERTY = "distribution";
    private static final String MODULES_PROPERTY = ModuleComposerDefaults.MODULES_KEY;
    private static final String INCLUDE_MODULES_PROPERTY = "includeModules";
    private static final String EXCLUDE_MODULES_PROPERTY = "excludeModules";
    private static final String PROFILE_PROPERTY = "profile";
    private static final String DEBUG_PROPERTY = "debug";
    private static final String VALIDATION_PROPERTY = "validation";
    private static final String GENERATED_DIRECTORY_NAME = "generated";
    private static final String MODULE_COMPOSER_DIRECTORY_NAME = "module-composer";
    private static final String DEFAULT_DISTRIBUTION_FILE = "distributions.yml";
    private static final String TEST_TASK_NAME = "test";

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

        TaskProvider<Task> bundleTest = registerTask(
                project,
                BUNDLE_TEST_TASK,
                "Tests one module directly or validates a generated combined host."
        );
        BundleTasks bundleTasks = new BundleTasks(bundleRun, bundleBuild, bundleTest);

        TaskProvider<ExplainModuleComposerTask> explain = registerExplainTask(
                project,
                EXPLAIN_TASK
        );

        DiscoveryTasks discoveryTasks =
                registerDiscoveryTasks(project);

        project.getGradle().projectsEvaluated(gradle ->
                configureDiscoveryTasks(project, extension, registry, discoveryTasks)
        );

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
                    configureExplainTask(project, task, extension, adapter, buildTool, plan)
            );

            if (plan.isStandalone()) {
                configureStandalone(
                        project,
                        buildTool,
                        adapter,
                        plan,
                        bundleTasks
                );
            } else {
                configureGeneratedHost(
                        project,
                        extension,
                        buildTool,
                        adapter,
                        plan,
                        bundleTasks
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

    private TaskProvider<ExplainModuleComposerTask> registerExplainTask(
            Project project,
            String name
    ) {
        return project.getTasks().register(name, ExplainModuleComposerTask.class, task -> {
            task.setGroup(TASK_GROUP);
            task.setDescription("Explains the selected execution and build plan.");
        });
    }

    private static void configureExplainTask(
            Project project,
            ExplainModuleComposerTask task,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            GradleBuildToolAdapter buildTool,
            CompositionPlan plan
    ) {
        task.getFramework().set(plan.framework());
        task.getBuildTool().set(buildTool.buildToolId());
        task.getExecutionMode().set(plan.executionMode().name());
        task.getAdapter().set(adapter.getClass().getSimpleName());
        task.getSelectionMode().set(plan.selectionMode().name());
        if (plan.distribution() != null) {
            task.getDistribution().set(plan.distribution());
        }
        task.getApplicationName().set(plan.applicationName());
        task.getValidation().set(validationMode(project));
        configureExplainArtifact(task, plan);
        configureExplainContainer(task, plan);
        task.getRuntimePort().set(String.valueOf(runtimePort(plan)));
        task.getModuleNames().set(plan.moduleNames());
        task.getStandalone().set(plan.isStandalone());
        configureExplainExecution(task, extension, adapter, buildTool, plan);
    }

    private static void configureExplainArtifact(
            ExplainModuleComposerTask task,
            CompositionPlan plan
    ) {
        if (plan.artifact() != null && plan.artifact().fileName() != null) {
            task.getArtifactFileName().set(plan.artifact().fileName());
        }
    }

    private static void configureExplainContainer(
            ExplainModuleComposerTask task,
            CompositionPlan plan
    ) {
        if (plan.container() == null) {
            return;
        }

        int hostPort = containerHostPort(plan.container());
        int containerPort = containerContainerPort(plan.container());
        task.getContainerSummary().set(
                containerImage(plan.container()) + ":" + hostPort + "->" + containerPort
        );
    }

    private static void configureExplainExecution(
            ExplainModuleComposerTask task,
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            GradleBuildToolAdapter buildTool,
            CompositionPlan plan
    ) {
        if (plan.isStandalone()) {
            ModuleRegistration module = plan.modules().get(0);
            task.getStandaloneRunTask().set(
                    buildTool.standaloneRun(adapter, module).displayName()
            );
            task.getStandaloneBuildTask().set(
                    buildTool.standaloneBuild(adapter, module).displayName()
            );
            return;
        }

        task.getGeneratedHostDirectory().set(
                generatedHostDirectory(extension, plan).getPath()
        );
        task.getBuildOutput().set(outputJar(extension, plan).getPath());
    }

    private DiscoveryTasks registerDiscoveryTasks(
            Project project
    ) {
        return new DiscoveryTasks(
                registerListModulesTask(project),
                registerListDistributionsTask(project)
        );
    }

    private TaskProvider<ListModulesTask> registerListModulesTask(Project project) {
        return project.getTasks().register(
                LIST_MODULES_TASK,
                ListModulesTask.class,
                task -> task.setGroup(TASK_GROUP)
        );
    }

    private TaskProvider<ListDistributionsTask> registerListDistributionsTask(
            Project project
    ) {
        return project.getTasks().register(
                LIST_DISTRIBUTIONS_TASK,
                ListDistributionsTask.class,
                task -> task.setGroup(TASK_GROUP)
        );
    }

    private static void configureDiscoveryTasks(
            Project project,
            ModuleComposerExtension extension,
            ModuleRegistry registry,
            DiscoveryTasks discoveryTasks
    ) {
        discoveryTasks.listModules().configure(task ->
                task.getModuleDescriptions().set(
                        registry.all().stream()
                                .map(ModuleComposerPlugin::moduleDescription)
                                .toList()
                )
        );
        discoveryTasks.listDistributions().configure(task ->
                configureListDistributionsTask(project, extension, task)
        );
    }

    private static String moduleDescription(ModuleRegistration module) {
        return PluginLogFormats.ITEM.replace("{}", module.name()) + System.lineSeparator() +
                "      project: " + module.projectPath() + System.lineSeparator() +
                "      configuration: " + module.configurationClass();
    }

    private static void configureListDistributionsTask(
            Project project,
            ModuleComposerExtension extension,
            ListDistributionsTask task
    ) {
        Path distributionPath = distributionPath(project, extension);
        task.getDistributionPath().set(distributionPath.toString());
        task.getDistributionFileName().set(extension.getDistributionFile().get());
        configureDistributionSources(project, distributionPath, task);
    }

    private static void configureDistributionSources(
            Project project,
            Path distributionPath,
            ListDistributionsTask task
    ) {
        File configured = distributionPath.toFile();
        if (configured.exists()) {
            task.getDistributionSources().from(configured);
        }

        Path parent = distributionPath.getParent() == null
                ? Path.of(".")
                : distributionPath.getParent();
        File defaultDirectory = parent.resolve(ModuleComposerDefaults.DISTRIBUTIONS_KEY)
                .toFile();
        if (defaultDirectory.isDirectory()) {
            task.getDistributionSources().from(project.fileTree(defaultDirectory));
        }
    }

    private void configureStandalone(
            Project root,
            GradleBuildToolAdapter buildTool,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            BundleTasks bundleTasks
    ) {
        ModuleRegistration module = plan.modules().get(0);
        BuildInvocation run = buildTool.standaloneRun(adapter, module);
        BuildInvocation build = buildTool.standaloneBuild(adapter, module);
        String test = module.projectPath() + ":" + TEST_TASK_NAME;
        List<String> validationTasks = validationTasks(root, plan);

        configureRunParameters(root, buildTool, run);

        bundleTasks.run().configure(task -> {
            task.dependsOn(validationTasks);
            task.dependsOn(run.steps());
        });
        bundleTasks.build().configure(task -> {
            task.dependsOn(validationTasks);
            task.dependsOn(build.steps());
        });
        bundleTasks.test().configure(task -> task.dependsOn(test));
    }

    private void configureGeneratedHost(
            Project root,
            ModuleComposerExtension extension,
            GradleBuildToolAdapter buildTool,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            BundleTasks bundleTasks
    ) {
        List<String> validationTasks = validationTasks(root, plan);
        GeneratedHostInputs inputs = generatedHostInputs(
                extension,
                buildTool,
                plan
        );
        File hostDirectory = generatedHostDirectory(extension, plan);
        GeneratedHostSetup setup = new GeneratedHostSetup(
                extension,
                adapter,
                plan,
                validationTasks,
                inputs,
                hostDirectory
        );

        TaskProvider<GenerateHostTask> prepare = registerPrepareGeneratedHost(
                root,
                setup
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
        TaskProvider<Exec> testGenerated = registerGeneratedHostTest(
                root,
                hostDirectory,
                prepare
        );
        TaskProvider<CopyGeneratedHostJarTask> copyJar = registerCopyGeneratedHostJar(
                root,
                setup,
                buildGenerated
        );

        bundleTasks.run().configure(task -> task.dependsOn(runGenerated));
        bundleTasks.build().configure(task -> task.dependsOn(copyJar));
        bundleTasks.test().configure(task -> task.dependsOn(testGenerated));
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
            GeneratedHostSetup setup
    ) {
        return root.getTasks().register(
                PREPARE_GENERATED_HOST_TASK,
                GenerateHostTask.class,
                task -> configurePrepareGeneratedHost(
                        task,
                        setup
                )
        );
    }

    private static void configurePrepareGeneratedHost(
            GenerateHostTask task,
            GeneratedHostSetup setup
    ) {
        task.setGroup(TASK_GROUP);
        task.dependsOn(setup.validationTasks());
        task.dependsOn(setup.inputs().jarTasks());
        task.getAdapterClassName().set(setup.adapter().getClass().getName());
        task.getFramework().set(setup.plan().framework());
        task.getSelectionMode().set(setup.plan().selectionMode().name());
        task.getHostDirectory().set(setup.hostDirectory());
        setup.inputs().jarPaths().forEach(task.getDependencyJarPaths()::add);
        task.getConfigurationClasses().set(setup.inputs().configurationClasses());
        task.getModuleNames().set(setup.plan().moduleNames());
        task.getModuleProjectPaths().set(
                setup.plan().modules().stream()
                        .map(ModuleRegistration::projectPath)
                        .toList()
        );
        task.getModuleConfigurationClasses().set(
                setup.plan().modules().stream()
                        .map(ModuleRegistration::configurationClass)
                        .toList()
        );
        task.getStandaloneRunTasks().set(
                setup.plan().modules().stream()
                        .map(ModuleRegistration::standaloneRunTask)
                        .toList()
        );
        task.getStandaloneBuildTasks().set(
                setup.plan().modules().stream()
                        .map(ModuleRegistration::standaloneBuildTask)
                        .toList()
        );
        task.getPlainJarTasks().set(
                setup.plan().modules().stream()
                        .map(ModuleRegistration::plainJarTask)
                        .toList()
        );
        task.getDistribution().set(
                setup.plan().distribution() == null ? "" : setup.plan().distribution()
        );
        task.getApplicationName().set(setup.plan().applicationName());
        task.getJavaVersion().set(setup.extension().getJavaVersion());
        task.getRuntimeDebug().set(setup.plan().runtimeOptions().debug());
        if (setup.plan().runtimeOptions().port() != null) {
            task.getRuntimePort().set(setup.plan().runtimeOptions().port());
        }
        if (setup.plan().runtimeOptions().profile() != null) {
            task.getRuntimeProfile().set(setup.plan().runtimeOptions().profile());
        }
        configureGeneratedHostArtifact(task, setup.plan());
        configureGeneratedHostContainer(task, setup.plan());
        task.getFrameworkOptions().put(
                ModuleComposerDefaults.SPRING_BOOT_VERSION_KEY,
                setup.extension().getSpringBootVersion()
        );
        task.getFrameworkOptions().put(
                ModuleComposerDefaults.DEPENDENCY_MANAGEMENT_VERSION_KEY,
                setup.extension().getDependencyManagementVersion()
        );
    }

    private static void configureGeneratedHostArtifact(
            GenerateHostTask task,
            CompositionPlan plan
    ) {
        if (plan.artifact() != null && plan.artifact().fileName() != null) {
            task.getArtifactFileName().set(plan.artifact().fileName());
        }
    }

    private static void configureGeneratedHostContainer(
            GenerateHostTask task,
            CompositionPlan plan
    ) {
        if (plan.container() == null) {
            return;
        }

        if (plan.container().image() != null) {
            task.getContainerImage().set(plan.container().image());
        }
        if (plan.container().baseImage() != null) {
            task.getContainerBaseImage().set(plan.container().baseImage());
        }
        if (plan.container().hostPort() != null) {
            task.getContainerHostPort().set(plan.container().hostPort());
        }
        if (plan.container().containerPort() != null) {
            task.getContainerPort().set(plan.container().containerPort());
        }
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

    private TaskProvider<Exec> registerGeneratedHostTest(
            Project root,
            File hostDirectory,
            TaskProvider<GenerateHostTask> prepare
    ) {
        return root.getTasks().register(
                TEST_GENERATED_HOST_TASK,
                Exec.class,
                task -> {
                    task.setGroup(TASK_GROUP);
                    task.dependsOn(prepare);
                    configureGeneratedGradleInvocation(
                            root,
                            hostDirectory,
                            task,
                            List.of(TEST_TASK_NAME),
                            false
                    );
                }
        );
    }

    private TaskProvider<CopyGeneratedHostJarTask> registerCopyGeneratedHostJar(
            Project root,
            GeneratedHostSetup setup,
            TaskProvider<Exec> buildGenerated
    ) {
        return root.getTasks().register(
                COPY_GENERATED_HOST_JAR_TASK,
                CopyGeneratedHostJarTask.class,
                task -> {
                    task.setGroup(TASK_GROUP);
                    task.dependsOn(buildGenerated);
                    configureCopyGeneratedHostJar(
                            task,
                            setup
                    );
                }
        );
    }

    private static void configureCopyGeneratedHostJar(
            CopyGeneratedHostJarTask task,
            GeneratedHostSetup setup
    ) {
        File source = setup.hostDirectory()
                .toPath()
                .resolve(setup.adapter().generatedArtifact())
                .toFile();
        File target = outputJar(setup.extension(), setup.plan());
        task.getSourceJar().set(source);
        task.getTargetJar().set(target);
        task.getApplicationName().set(setup.plan().applicationName());
        task.getContainerEnabled().set(setup.plan().container() != null);
        task.getContainerDirectory().set(containerOutputDirectory(setup.plan(), target));
        configureContainerInputs(task, setup.plan());
    }

    private static void configureContainerInputs(
            CopyGeneratedHostJarTask task,
            CompositionPlan plan
    ) {
        if (plan.container() == null) {
            return;
        }

        if (plan.container().image() != null) {
            task.getContainerImage().set(plan.container().image());
        }
        if (plan.container().baseImage() != null) {
            task.getContainerBaseImage().set(plan.container().baseImage());
        }
        if (plan.container().hostPort() != null) {
            task.getContainerHostPort().set(plan.container().hostPort());
        }
        if (plan.container().containerPort() != null) {
            task.getContainerPort().set(plan.container().containerPort());
        }
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
        return isWindows() ? GradleBuildToolAdapter.ID + ".bat" : GradleBuildToolAdapter.ID;
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
                                || name.equals(BUNDLE_TEST_TASK)
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

    private static File containerOutputDirectory(
            CompositionPlan plan,
            File outputJar
    ) {
        return outputJar.getParentFile()
                .toPath()
                .resolve("containers")
                .resolve(CopyGeneratedHostJarTask.containerServiceName(plan.applicationName()))
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

    private static Object runtimePort(CompositionPlan plan) {
        return plan.runtimeOptions().port() == null
                ? "default"
                : plan.runtimeOptions().port();
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

    static String containerImage(DistributionContainer container) {
        return container.image() == null ? "(no image)" : container.image();
    }

    static int containerHostPort(DistributionContainer container) {
        if (container.hostPort() != null) {
            return container.hostPort();
        }
        if (container.containerPort() != null) {
            return container.containerPort();
        }
        return 8080;
    }

    static int containerContainerPort(DistributionContainer container) {
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

    private record GeneratedHostSetup(
            ModuleComposerExtension extension,
            FrameworkAdapter adapter,
            CompositionPlan plan,
            List<String> validationTasks,
            GeneratedHostInputs inputs,
            File hostDirectory
    ) {
    }

    private record BundleTasks(
            TaskProvider<Task> run,
            TaskProvider<Task> build,
            TaskProvider<Task> test
    ) {
    }

    private record DiscoveryTasks(
            TaskProvider<ListModulesTask> listModules,
            TaskProvider<ListDistributionsTask> listDistributions
    ) {
    }
}
