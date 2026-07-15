package io.github.bud127.modulecomposer.core;

import java.util.List;

public record SelectionRequest(
        List<String> modules,
        String distribution,
        String applicationName,
        List<String> includeModules,
        List<String> excludeModules,
        RuntimeOptions runtimeOptions
) {
}
