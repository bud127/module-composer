package io.github.bud127.modulecomposer.plugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

public abstract class ListModulesTask extends DefaultTask {

    @Input
    public abstract ListProperty<String> getModuleDescriptions();

    @TaskAction
    public void listModules() {
        getLogger().lifecycle("Available modules:");
        if (getModuleDescriptions().get().isEmpty()) {
            getLogger().lifecycle("  (none registered)");
            return;
        }

        getModuleDescriptions().get().forEach(description ->
                getLogger().lifecycle(description)
        );
    }
}
