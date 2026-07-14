package io.github.bud127.modulecomposer.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleRegistry {

    private final Map<String, ModuleRegistration> modules =
            new LinkedHashMap<>();

    public void register(ModuleRegistration module) {
        ModuleRegistration existing = modules.get(module.name());

        if (existing != null
                && !existing.projectPath().equals(module.projectPath())) {
            throw new ModuleComposerException(
                    "Duplicate Module Composer module name '" +
                            module.name() +
                            "' registered by " +
                            existing.projectPath() +
                            " and " +
                            module.projectPath() +
                            ". Module names must be unique."
            );
        }

        modules.put(module.name(), module);
    }

    public ModuleRegistration get(String name) {
        return modules.get(name);
    }

    public Map<String, ModuleRegistration> modules() {
        return Collections.unmodifiableMap(modules);
    }

    public Collection<ModuleRegistration> all() {
        return modules.values();
    }
}
