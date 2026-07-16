package io.github.bud127.modulecomposer.plugin;

import io.github.bud127.modulecomposer.core.DistributionConfig;
import io.github.bud127.modulecomposer.core.DistributionLoader;
import io.github.bud127.modulecomposer.core.DistributionPreset;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.nio.file.Path;

public abstract class ListDistributionsTask extends DefaultTask {

    private static final String ITEM_LOG_FORMAT = "  + {}";

    @Input
    public abstract Property<String> getDistributionPath();

    @Input
    public abstract Property<String> getDistributionFileName();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDistributionSources();

    @TaskAction
    public void listDistributions() {
        DistributionLoader loader =
                new DistributionLoader(Path.of(getDistributionPath().get()));

        if (!loader.exists()) {
            getLogger().lifecycle(
                    "No {} file found.",
                    getDistributionFileName().get()
            );
            getLogger().lifecycle("Distribution presets are optional.");
            return;
        }

        DistributionConfig config = loader.loadRequired();

        getLogger().lifecycle("Available distributions:");
        config.distributions().forEach(this::logDistribution);
    }

    private void logDistribution(String name, DistributionPreset preset) {
        logDistributionHeader(name, preset);
        logDistributionArtifact(preset);
        logDistributionContainer(preset);
    }

    private void logDistributionHeader(String name, DistributionPreset preset) {
        if (preset.applicationName() == null) {
            getLogger().lifecycle(ITEM_LOG_FORMAT, name);
            return;
        }

        getLogger().lifecycle(
                "  + {} (application: {})",
                name,
                preset.applicationName()
        );
    }

    private void logDistributionArtifact(DistributionPreset preset) {
        if (preset.artifact() == null || preset.artifact().fileName() == null) {
            return;
        }

        getLogger().lifecycle(
                "      artifact: {}",
                preset.artifact().fileName()
        );
    }

    private void logDistributionContainer(DistributionPreset preset) {
        if (preset.container() == null) {
            return;
        }

        int hostPort = ModuleComposerPlugin.containerHostPort(preset.container());
        int containerPort = ModuleComposerPlugin.containerContainerPort(preset.container());
        getLogger().lifecycle(
                "      container: {}:{}->{}",
                ModuleComposerPlugin.containerImage(preset.container()),
                hostPort,
                containerPort
        );
    }
}
