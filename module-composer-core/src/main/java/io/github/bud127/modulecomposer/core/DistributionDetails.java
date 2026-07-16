package io.github.bud127.modulecomposer.core;

public record DistributionDetails(
        String distribution,
        String applicationName,
        DistributionArtifact artifact,
        DistributionContainer container
) {
}
