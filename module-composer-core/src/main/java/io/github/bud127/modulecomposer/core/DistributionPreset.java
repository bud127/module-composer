package io.github.bud127.modulecomposer.core;

import java.util.List;

public record DistributionPreset(
        List<String> modules,
        String applicationName,
        DistributionArtifact artifact,
        DistributionContainer container
) {
}
