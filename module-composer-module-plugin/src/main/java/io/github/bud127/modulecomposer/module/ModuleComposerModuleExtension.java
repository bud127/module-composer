package io.github.bud127.modulecomposer.module;

import org.gradle.api.provider.Property;

public abstract class ModuleComposerModuleExtension {

    public abstract Property<String> getName();

    public abstract Property<String> getConfigurationClass();

    public abstract Property<String> getStandaloneRunTask();

    public abstract Property<String> getStandaloneBuildTask();

    public abstract Property<String> getPlainJarTask();
}
