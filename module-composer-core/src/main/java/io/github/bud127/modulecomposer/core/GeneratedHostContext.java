package io.github.bud127.modulecomposer.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record GeneratedHostContext(
        Path hostDirectory,
        List<String> dependencyJarPaths,
        List<String> configurationClasses,
        List<String> moduleNames,
        String distribution,
        String applicationName,
        int javaVersion,
        Map<String, String> frameworkOptions
) {
}
