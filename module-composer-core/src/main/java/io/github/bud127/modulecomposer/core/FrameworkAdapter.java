package io.github.bud127.modulecomposer.core;

import java.io.IOException;
import java.nio.file.Path;

public interface FrameworkAdapter {
    String frameworkId();

    void generateHost(
            CompositionPlan plan,
            GeneratedHostContext context
    ) throws IOException;

    String standaloneRunTask(ModuleRegistration module);

    String standaloneBuildTask(ModuleRegistration module);

    String generatedRunTask();

    String generatedBuildTask();

    Path generatedArtifact();
}
