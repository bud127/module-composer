package io.github.bud127.modulecomposer.plugin;

import io.github.bud127.modulecomposer.core.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates the temporary application host used for composed multi-module runs
 * and builds.
 */
public abstract class GenerateHostTask extends DefaultTask {

    /**
     * Directory where the generated host project is written.
     *
     * @return generated host directory
     */
    @OutputDirectory
    public abstract DirectoryProperty getHostDirectory();

    /**
     * Absolute paths to module or shared-library JAR files consumed by the host.
     *
     * @return dependency JAR paths
     */
    @Input
    public abstract ListProperty<String> getDependencyJarPaths();

    /**
     * Framework configuration classes imported by the generated host.
     *
     * @return configuration class names
     */
    @Input
    public abstract ListProperty<String> getConfigurationClasses();

    /**
     * Logical module names included in the generated host.
     *
     * @return selected module names
     */
    @Input
    public abstract ListProperty<String> getModuleNames();

    /**
     * Optional distribution preset name used for the current composition.
     *
     * @return distribution name
     */
    @Input
    public abstract Property<String> getDistribution();

    /**
     * Application name used by the generated host and final bundle.
     *
     * @return application name
     */
    @Input
    public abstract Property<String> getApplicationName();

    /**
     * Java version used by the generated host build.
     *
     * @return Java language version
     */
    @Input
    public abstract Property<Integer> getJavaVersion();

    /**
     * Framework-specific options passed to the selected framework adapter.
     *
     * @return framework option map
     */
    @Input
    public abstract MapProperty<String, String> getFrameworkOptions();

    @Input
    public abstract Property<String> getAdapterClassName();

    @Input
    public abstract Property<String> getFramework();

    @Input
    public abstract Property<String> getSelectionMode();

    @Input
    public abstract ListProperty<String> getModuleProjectPaths();

    @Input
    public abstract ListProperty<String> getModuleConfigurationClasses();

    @Input
    public abstract ListProperty<String> getStandaloneRunTasks();

    @Input
    public abstract ListProperty<String> getStandaloneBuildTasks();

    @Input
    public abstract ListProperty<String> getPlainJarTasks();

    @Input
    @Optional
    public abstract Property<Integer> getRuntimePort();

    @Input
    @Optional
    public abstract Property<String> getRuntimeProfile();

    @Input
    public abstract Property<Boolean> getRuntimeDebug();

    @Input
    @Optional
    public abstract Property<String> getArtifactFileName();

    @Input
    @Optional
    public abstract Property<String> getContainerImage();

    @Input
    @Optional
    public abstract Property<String> getContainerBaseImage();

    @Input
    @Optional
    public abstract Property<Integer> getContainerHostPort();

    @Input
    @Optional
    public abstract Property<Integer> getContainerPort();

    /**
     * Generates the host project by delegating to the selected framework adapter.
     *
     * @throws IOException if host files cannot be written
     */
    @TaskAction
    public void generate() throws IOException {
        Path host = getHostDirectory().get().getAsFile().toPath();
        FrameworkAdapter adapter = instantiateAdapter();
        adapter.generateHost(
                compositionPlan(),
                new GeneratedHostContext(
                        host,
                        getDependencyJarPaths().get(),
                        getConfigurationClasses().get(),
                        getModuleNames().get(),
                        getDistribution().getOrElse(""),
                        getApplicationName().get(),
                        getJavaVersion().get(),
                        getFrameworkOptions().get()
                )
        );

        getLogger().lifecycle("Generated combined host: {}", host);
    }

    private FrameworkAdapter instantiateAdapter() {
        String className = getAdapterClassName().get();
        try {
            return (FrameworkAdapter) Class.forName(className)
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Unable to create framework adapter " + className,
                    exception
            );
        }
    }

    private CompositionPlan compositionPlan() {
        return new CompositionPlan(
                getFramework().get(),
                ExecutionMode.GENERATED_HOST,
                SelectionMode.valueOf(getSelectionMode().get()),
                moduleRegistrations(),
                runtimeOptions(),
                blankToNull(getDistribution().getOrElse("")),
                getApplicationName().get(),
                distributionArtifact(),
                distributionContainer()
        );
    }

    private List<ModuleRegistration> moduleRegistrations() {
        List<String> names = getModuleNames().get();
        List<String> projectPaths = getModuleProjectPaths().get();
        List<String> configurations = getModuleConfigurationClasses().get();
        List<String> runTasks = getStandaloneRunTasks().get();
        List<String> buildTasks = getStandaloneBuildTasks().get();
        List<String> jarTasks = getPlainJarTasks().get();
        List<ModuleRegistration> modules = new ArrayList<>();
        for (int index = 0; index < names.size(); index++) {
            modules.add(new ModuleRegistration(
                    names.get(index),
                    projectPaths.get(index),
                    configurations.get(index),
                    runTasks.get(index),
                    buildTasks.get(index),
                    jarTasks.get(index)
            ));
        }
        return modules;
    }

    private RuntimeOptions runtimeOptions() {
        return new RuntimeOptions(
                getRuntimePort().getOrNull(),
                getRuntimeProfile().getOrElse(""),
                getRuntimeDebug().get(),
                List.of(),
                Map.of()
        );
    }

    private DistributionArtifact distributionArtifact() {
        String fileName = getArtifactFileName().getOrNull();
        return fileName == null ? null : new DistributionArtifact(fileName);
    }

    private DistributionContainer distributionContainer() {
        String image = getContainerImage().getOrNull();
        String baseImage = getContainerBaseImage().getOrNull();
        Integer hostPort = getContainerHostPort().getOrNull();
        Integer containerPort = getContainerPort().getOrNull();
        if (image == null && baseImage == null && hostPort == null && containerPort == null) {
            return null;
        }
        return new DistributionContainer(image, baseImage, hostPort, containerPort);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
