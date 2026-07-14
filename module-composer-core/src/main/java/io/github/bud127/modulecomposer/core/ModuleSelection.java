package io.github.bud127.modulecomposer.core;

import java.util.List;

public record ModuleSelection(
        List<ModuleRegistration> modules,
        String distribution,
        RuntimeOptions runtimeOptions,
        SelectionMode mode
) {
    public boolean isStandalone() {
        return modules.size() == 1;
    }

    public List<String> moduleNames() {
        return modules.stream().map(ModuleRegistration::name).toList();
    }
}
