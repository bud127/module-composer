package io.github.bud127.modulecomposer.core;

import java.util.List;
import java.util.Map;

public record RuntimeOptions(
        Integer port,
        String profile,
        boolean debug,
        List<String> jvmArgs,
        Map<String, String> environmentVariables
) {
    public static RuntimeOptions none() {
        return new RuntimeOptions(null, "", false, List.of(), Map.of());
    }
}
