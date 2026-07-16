package io.github.bud127.modulecomposer.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record GeneratedHostContext(
        Path hostDirectory,
        GeneratedHostClasspath classpath,
        GeneratedHostMetadata metadata
) {
    public List<String> dependencyJarPaths() {
        return classpath.dependencyJarPaths();
    }

    public List<String> configurationClasses() {
        return classpath.configurationClasses();
    }

    public List<String> moduleNames() {
        return metadata.moduleNames();
    }

    public String distribution() {
        return metadata.distribution();
    }

    public String applicationName() {
        return metadata.applicationName();
    }

    public int javaVersion() {
        return metadata.javaVersion();
    }

    public Map<String, String> frameworkOptions() {
        return metadata.frameworkOptions();
    }
}
