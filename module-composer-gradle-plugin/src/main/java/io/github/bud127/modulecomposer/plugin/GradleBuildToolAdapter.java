package io.github.bud127.modulecomposer.plugin;

import io.github.bud127.modulecomposer.core.BuildInvocation;
import io.github.bud127.modulecomposer.core.BuildToolAdapter;
import io.github.bud127.modulecomposer.core.FrameworkAdapter;
import io.github.bud127.modulecomposer.core.ModuleRegistration;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public final class GradleBuildToolAdapter implements BuildToolAdapter {

    private final Project root;

    public GradleBuildToolAdapter(Project root) {
        this.root = root;
    }

    @Override
    public String buildToolId() {
        return "gradle";
    }

    @Override
    public boolean projectExists(String projectReference) {
        return root.getRootProject().findProject(projectReference) != null;
    }

    @Override
    public BuildInvocation standaloneRun(
            FrameworkAdapter frameworkAdapter,
            ModuleRegistration module
    ) {
        String task = frameworkAdapter.standaloneRunTask(module);
        return BuildInvocation.of(task, task);
    }

    @Override
    public BuildInvocation standaloneBuild(
            FrameworkAdapter frameworkAdapter,
            ModuleRegistration module
    ) {
        String task = frameworkAdapter.standaloneBuildTask(module);
        return BuildInvocation.of(task, task);
    }

    @Override
    public BuildInvocation moduleArtifact(ModuleRegistration module) {
        return BuildInvocation.of(module.plainJarTask(), module.plainJarTask());
    }

    @Override
    public BuildInvocation projectArtifact(String projectReference) {
        String task = projectReference + ":jar";
        return BuildInvocation.of(task, task);
    }

    @Override
    public BuildInvocation generatedRun(FrameworkAdapter frameworkAdapter) {
        return BuildInvocation.of(
                frameworkAdapter.generatedRunTask(),
                frameworkAdapter.generatedRunTask()
        );
    }

    @Override
    public BuildInvocation generatedBuild(FrameworkAdapter frameworkAdapter) {
        return new BuildInvocation(
                frameworkAdapter.generatedBuildTask(),
                java.util.List.of("clean", frameworkAdapter.generatedBuildTask())
        );
    }

    public Provider<String> archiveFilePath(String taskPath) {
        Project targetProject = projectForTaskPath(taskPath);
        String taskName = taskName(taskPath);

        return targetProject.getTasks()
                .named(taskName, AbstractArchiveTask.class)
                .flatMap(AbstractArchiveTask::getArchiveFile)
                .map(file -> file.getAsFile().getAbsolutePath());
    }

    public Project projectForTaskPath(String taskPath) {
        int index = taskPath.lastIndexOf(':');
        if (index < 0) {
            return root;
        }

        String projectPath = taskPath.substring(0, index);
        if (projectPath.isEmpty()) {
            return root;
        }

        return root.project(projectPath);
    }

    public String taskName(String taskPath) {
        int index = taskPath.lastIndexOf(':');
        return index < 0 ? taskPath : taskPath.substring(index + 1);
    }
}
