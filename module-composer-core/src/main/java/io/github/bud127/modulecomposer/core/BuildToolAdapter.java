package io.github.bud127.modulecomposer.core;

public interface BuildToolAdapter {

    String buildToolId();

    boolean projectExists(String projectReference);

    BuildInvocation standaloneRun(
            FrameworkAdapter frameworkAdapter,
            ModuleRegistration module
    );

    BuildInvocation standaloneBuild(
            FrameworkAdapter frameworkAdapter,
            ModuleRegistration module
    );

    BuildInvocation moduleArtifact(ModuleRegistration module);

    BuildInvocation projectArtifact(String projectReference);

    BuildInvocation generatedRun(FrameworkAdapter frameworkAdapter);

    BuildInvocation generatedBuild(FrameworkAdapter frameworkAdapter);
}
