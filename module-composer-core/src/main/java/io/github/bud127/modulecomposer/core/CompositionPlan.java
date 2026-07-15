package io.github.bud127.modulecomposer.core;

import java.util.List;

public record CompositionPlan(
        String framework,
        ExecutionMode executionMode,
        SelectionMode selectionMode,
        List<ModuleRegistration> modules,
        RuntimeOptions runtimeOptions,
        String distribution,
        String applicationName
) {
    public boolean isStandalone() {
        return executionMode == ExecutionMode.STANDALONE;
    }

    public List<String> moduleNames() {
        return modules.stream().map(ModuleRegistration::name).toList();
    }
}
