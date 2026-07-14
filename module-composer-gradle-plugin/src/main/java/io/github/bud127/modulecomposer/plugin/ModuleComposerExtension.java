package io.github.bud127.modulecomposer.plugin;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;
import java.util.List;

public abstract class ModuleComposerExtension {

    public abstract Property<String> getDistributionFile();
    public abstract Property<String> getFramework();
    public abstract DirectoryProperty getGeneratedHostDirectory();
    public abstract RegularFileProperty getOutputJar();
    public abstract Property<String> getSpringBootVersion();
    public abstract Property<String> getDependencyManagementVersion();
    public abstract Property<Integer> getJavaVersion();
    public abstract ListProperty<String> getCommonProjectPaths();
    public abstract ListProperty<String> getCommonConfigurationClasses();

    @Inject
    public ModuleComposerExtension(ObjectFactory objects, ProjectLayout layout) {
        getDistributionFile().convention("distributions.yml");
        getFramework().convention("spring-boot");
        getGeneratedHostDirectory().convention(
                layout.getBuildDirectory().dir("module-composer/combined-app")
        );
        getOutputJar().convention(
                layout.getBuildDirectory().file("module-composer/output/combined-app.jar")
        );
        getSpringBootVersion().convention("3.5.7");
        getDependencyManagementVersion().convention("1.1.7");
        getJavaVersion().convention(21);
        getCommonProjectPaths().convention(List.of());
        getCommonConfigurationClasses().convention(List.of());
    }
}
