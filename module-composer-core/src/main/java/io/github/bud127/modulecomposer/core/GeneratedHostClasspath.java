package io.github.bud127.modulecomposer.core;

import java.util.List;

public record GeneratedHostClasspath(
        List<String> dependencyJarPaths,
        List<String> configurationClasses
) {
}
