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

public abstract class GenerateHostTask extends DefaultTask {

    private FrameworkAdapter adapter;
    private CompositionPlan plan;

    @OutputDirectory
    public abstract DirectoryProperty getHostDirectory();

    @Input
    public abstract ListProperty<String> getDependencyJarPaths();

    @Input
    public abstract ListProperty<String> getConfigurationClasses();

    @Input
    public abstract ListProperty<String> getModuleNames();

    @Input
    public abstract Property<String> getDistribution();

    @Input
    public abstract Property<Integer> getJavaVersion();

    @Input
    public abstract MapProperty<String, String> getFrameworkOptions();

    @Internal
    public FrameworkAdapter getAdapter() {
        return adapter;
    }

    public void setAdapter(FrameworkAdapter adapter) {
        this.adapter = adapter;
    }

    @Internal
    public CompositionPlan getPlan() {
        return plan;
    }

    public void setPlan(CompositionPlan plan) {
        this.plan = plan;
    }

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
                        getJavaVersion().get(),
                        getFrameworkOptions().get()
                )
        );

        getLogger().lifecycle("Generated combined host: {}", host);
    }
}
