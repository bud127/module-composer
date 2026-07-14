package io.github.bud127.modulecomposer.module;

import io.github.bud127.modulecomposer.core.ModuleRegistration;
import io.github.bud127.modulecomposer.core.ModuleRegistry;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public final class ModuleComposerModulePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        if (project == project.getRootProject()) {
            throw new GradleException(
                    "Module Composer module plugin must be applied to a module project, not the root project."
            );
        }

        ModuleComposerModuleExtension extension =
                project.getExtensions().create(
                        "moduleComposerModule",
                        ModuleComposerModuleExtension.class
                );

        extension.getName().convention(defaultModuleName(project));

        project.afterEvaluate(ignored -> registry(project.getRootProject())
                .register(toRegistration(project, extension)));
    }

    public static ModuleRegistry registry(Project root) {
        ModuleRegistry existing =
                root.getExtensions().findByType(ModuleRegistry.class);

        if (existing != null) {
            return existing;
        }

        return root.getExtensions().create(
                "moduleComposerRegistry",
                ModuleRegistry.class
        );
    }

    private static ModuleRegistration toRegistration(
            Project project,
            ModuleComposerModuleExtension extension
    ) {
        if (!extension.getConfigurationClass().isPresent()
                || extension.getConfigurationClass().get().isBlank()) {
            throw new GradleException(
                    "Module Composer module '" +
                            extension.getName().get() +
                            "' in project " +
                            project.getPath() +
                            " must define moduleComposerModule.configurationClass."
            );
        }

        return new ModuleRegistration(
                normalize(extension.getName().get()),
                project.getPath(),
                extension.getConfigurationClass().get(),
                absoluteTaskPath(project, extension.getStandaloneRunTask().get()),
                absoluteTaskPath(project, extension.getStandaloneBuildTask().get()),
                absoluteTaskPath(project, extension.getPlainJarTask().get())
        );
    }

    private static String defaultModuleName(Project project) {
        return normalize(project.getName());
    }

    private static String normalize(String name) {
        return name.trim().replaceFirst("^module-", "");
    }

    private static String absoluteTaskPath(Project project, String task) {
        String value = task.trim();
        if (value.startsWith(":")) {
            return value;
        }

        return project.getPath() + ":" + value;
    }
}
