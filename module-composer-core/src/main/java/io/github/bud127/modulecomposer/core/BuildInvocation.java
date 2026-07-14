package io.github.bud127.modulecomposer.core;

import java.util.List;

public record BuildInvocation(
        String displayName,
        List<String> steps
) {
    public static BuildInvocation of(String displayName, String step) {
        return new BuildInvocation(displayName, List.of(step));
    }
}
