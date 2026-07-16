package io.github.bud127.modulecomposer.core;

import java.util.List;
import java.util.Map;

public record GeneratedHostMetadata(
        List<String> moduleNames,
        String distribution,
        String applicationName,
        int javaVersion,
        Map<String, String> frameworkOptions
) {
}
