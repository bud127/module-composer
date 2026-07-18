package io.github.bud127.modulecomposer.plugin;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

/**
 * Root project extension for configuring Module Composer.
 */
public abstract class ModuleComposerExtension {

    /**
     * YAML file containing reusable distribution presets.
     *
     * @return distribution file path relative to the root project
     */
    public abstract Property<String> getDistributionFile();

    /**
     * Framework adapter identifier used for composition.
     *
     * @return framework identifier
     */
    public abstract Property<String> getFramework();

    /**
     * Directory where the temporary generated host project is written.
     *
     * @return generated host directory
     */
    public abstract DirectoryProperty getGeneratedHostDirectory();

    /**
     * Final combined executable JAR output file.
     *
     * @return combined output JAR
     */
    public abstract RegularFileProperty getOutputJar();

    /**
     * Spring Boot version used by the Spring Boot framework adapter.
     *
     * @return Spring Boot version
     */
    public abstract Property<String> getSpringBootVersion();

    /**
     * Spring dependency management plugin version for generated hosts.
     *
     * @return dependency management plugin version
     */
    public abstract Property<String> getDependencyManagementVersion();

    /**
     * Quarkus platform and Gradle plugin version used by generated Quarkus hosts.
     *
     * @return Quarkus version
     */
    public abstract Property<String> getQuarkusVersion();

    /**
     * Java language version used by generated hosts.
     *
     * @return Java language version
     */
    public abstract Property<Integer> getJavaVersion();

    /**
     * Shared Gradle project paths included in generated hosts.
     *
     * @return shared project paths
     */
    public abstract ListProperty<String> getCommonProjectPaths();

    /**
     * Shared framework configuration classes imported by generated hosts.
     *
     * @return shared configuration class names
     */
    public abstract ListProperty<String> getCommonConfigurationClasses();
}
