package io.github.bud127.modulecomposer.plugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

public abstract class ExplainModuleComposerTask extends DefaultTask {

    private static final String ITEM_LOG_FORMAT = "  + {}";

    @Input
    public abstract Property<String> getFramework();

    @Input
    public abstract Property<String> getBuildTool();

    @Input
    public abstract Property<String> getExecutionMode();

    @Input
    public abstract Property<String> getAdapter();

    @Input
    public abstract Property<String> getSelectionMode();

    @Input
    @Optional
    public abstract Property<String> getDistribution();

    @Input
    public abstract Property<String> getApplicationName();

    @Input
    public abstract Property<String> getValidation();

    @Input
    @Optional
    public abstract Property<String> getArtifactFileName();

    @Input
    @Optional
    public abstract Property<String> getContainerSummary();

    @Input
    public abstract Property<String> getRuntimePort();

    @Input
    public abstract ListProperty<String> getModuleNames();

    @Input
    public abstract Property<Boolean> getStandalone();

    @Input
    @Optional
    public abstract Property<String> getStandaloneRunTask();

    @Input
    @Optional
    public abstract Property<String> getStandaloneBuildTask();

    @Input
    @Optional
    public abstract Property<String> getGeneratedHostDirectory();

    @Input
    @Optional
    public abstract Property<String> getBuildOutput();

    @TaskAction
    public void explain() {
        logSummary();
        logModules();
        logExecution();
        getLogger().lifecycle("");
    }

    private void logSummary() {
        getLogger().lifecycle("");
        getLogger().lifecycle("Module Composer");
        getLogger().lifecycle("---------------");
        getLogger().lifecycle("Framework      : {}", getFramework().get());
        getLogger().lifecycle("Build tool     : {}", getBuildTool().get());
        getLogger().lifecycle("Execution      : {}", getExecutionMode().get());
        getLogger().lifecycle("Adapter        : {}", getAdapter().get());
        getLogger().lifecycle("Selection mode : {}", getSelectionMode().get());
        logOptional("Distribution   : {}", getDistribution().getOrNull());
        getLogger().lifecycle("Application    : {}", getApplicationName().get());
        getLogger().lifecycle("Validation     : {}", getValidation().get());
        logOptional("Artifact       : {}", getArtifactFileName().getOrNull());
        logOptional("Container      : {}", getContainerSummary().getOrNull());
        getLogger().lifecycle("Port           : {}", getRuntimePort().get());
    }

    private void logModules() {
        getLogger().lifecycle("Modules:");
        getModuleNames().get().forEach(
                name -> getLogger().lifecycle(ITEM_LOG_FORMAT, name)
        );
    }

    private void logExecution() {
        if (getStandalone().get()) {
            getLogger().lifecycle("Run task:");
            getLogger().lifecycle("  {}", getStandaloneRunTask().get());
            getLogger().lifecycle("Build task:");
            getLogger().lifecycle("  {}", getStandaloneBuildTask().get());
            return;
        }

        getLogger().lifecycle("Generated Host:");
        getLogger().lifecycle("  {}", getGeneratedHostDirectory().get());
        getLogger().lifecycle("Build output:");
        getLogger().lifecycle("  {}", getBuildOutput().get());
    }

    private void logOptional(String pattern, String value) {
        if (value != null) {
            getLogger().lifecycle(pattern, value);
        }
    }
}
