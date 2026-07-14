package io.github.bud127.modulecomposer.module;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

public abstract class ModuleComposerModuleExtension {

    public abstract Property<String> getName();

    public abstract Property<String> getConfigurationClass();

    public abstract Property<String> getStandaloneRunTask();

    public abstract Property<String> getStandaloneBuildTask();

    public abstract Property<String> getPlainJarTask();

    @Inject
    public ModuleComposerModuleExtension(ObjectFactory objects) {
        getStandaloneRunTask().convention("bootRun");
        getStandaloneBuildTask().convention("bootJar");
        getPlainJarTask().convention("jar");
    }
}
