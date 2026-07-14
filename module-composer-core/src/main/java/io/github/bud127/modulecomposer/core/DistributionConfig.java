package io.github.bud127.modulecomposer.core;

import java.util.List;
import java.util.Map;

public record DistributionConfig(
        Map<String, List<String>> distributions
) {
}
