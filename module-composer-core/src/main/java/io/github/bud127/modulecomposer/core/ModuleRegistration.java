package io.github.bud127.modulecomposer.core;

public record ModuleRegistration(
        String name,
        String projectPath,
        String configurationClass,
        String standaloneRunTask,
        String standaloneBuildTask,
        String plainJarTask
) {
}
