package io.github.bud127.modulecomposer.core;

import java.util.LinkedHashMap;
import java.util.Map;

public final class FrameworkAdapterRegistry {

    private final Map<String, FrameworkAdapter> adapters =
            new LinkedHashMap<>();

    public void register(FrameworkAdapter adapter) {
        adapters.put(adapter.frameworkId(), adapter);
    }

    public FrameworkAdapter require(String frameworkId) {
        FrameworkAdapter adapter = adapters.get(frameworkId);
        if (adapter == null) {
            throw new ModuleComposerException(
                    "Unknown framework '" + frameworkId +
                            "'. Available frameworks: " + adapters.keySet()
            );
        }
        return adapter;
    }
}
