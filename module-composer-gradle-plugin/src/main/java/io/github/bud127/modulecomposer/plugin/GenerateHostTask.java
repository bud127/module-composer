package io.github.bud127.modulecomposer.plugin;

import io.github.bud127.modulecomposer.core.CompositionPlan;
import io.github.bud127.modulecomposer.core.FrameworkAdapter;
import io.github.bud127.modulecomposer.core.GeneratedHostContext;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Generates the temporary application host used for composed multi-module runs
 * and builds.
 */
public abstract class GenerateHostTask extends DefaultTask {

    private FrameworkAdapter adapter;
    private CompositionPlan plan;

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

    /**
     * Framework adapter responsible for generating the host project.
     *
     * @return selected framework adapter
     */
    @Internal
    public FrameworkAdapter getAdapter() {
        return adapter;
    }

    /**
     * Sets the framework adapter used by this task.
     *
     * @param adapter selected framework adapter
     */
    public void setAdapter(FrameworkAdapter adapter) {
        this.adapter = adapter;
    }

    /**
     * Composition plan used as input for host generation.
     *
     * @return composition plan
     */
    @Internal
    public CompositionPlan getPlan() {
        return plan;
    }

    /**
     * Sets the composition plan used by this task.
     *
     * @param plan composition plan
     */
    public void setPlan(CompositionPlan plan) {
        this.plan = plan;
    }

    /**
     * Generates the host project by delegating to the selected framework adapter.
     *
     * @throws IOException if host files cannot be written
     */
    @TaskAction
    public void generate() throws IOException {
        Path host = getHostDirectory().get().getAsFile().toPath();
        adapter.generateHost(
                plan,
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
}
