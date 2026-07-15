package io.github.bud127.modulecomposer.core;

import java.util.Map;

public record DistributionConfig(
        Map<String, DistributionPreset> distributions
) {
}
