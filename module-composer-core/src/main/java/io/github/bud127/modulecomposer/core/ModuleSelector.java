package io.github.bud127.modulecomposer.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModuleSelector {

    private final ModuleRegistry registry;
    private final DistributionLoader distributionLoader;
    private final ProjectValidator projectValidator;

    public ModuleSelector(
            ModuleRegistry registry,
            DistributionLoader distributionLoader,
            ProjectValidator projectValidator
    ) {
        this.registry = registry;
        this.distributionLoader = distributionLoader;
        this.projectValidator = projectValidator;
    }

    public ModuleSelection resolve(SelectionRequest request) {
        List<String> cliModules = normalizeAll(request.modules());
        List<String> includes = normalizeAll(request.includeModules());
        Set<String> excludes = new LinkedHashSet<>(
                normalizeAll(request.excludeModules())
        );
        String distribution = request.distribution() == null
                ? null
                : request.distribution().trim();

        if (!cliModules.isEmpty()
                && distribution != null
                && !distribution.isBlank()) {
            throw new ModuleComposerException(
                    "Module selection is ambiguous. Use either -Pmodules or -Pdistribution, not both."
            );
        }

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        SelectionMode mode;

        if (!cliModules.isEmpty()) {
            selected.addAll(cliModules);
            mode = SelectionMode.CLI;
        } else if (distribution != null && !distribution.isBlank()) {
            DistributionConfig config = distributionLoader.loadRequired();
            List<String> distributionModules =
                    config.distributions().get(distribution);

            if (distributionModules == null) {
                throw new ModuleComposerException(
                        "Unknown distribution '" + distribution +
                                "'. Available distributions: " +
                                config.distributions().keySet()
                );
            }

            selected.addAll(normalizeAll(distributionModules));
            mode = SelectionMode.DISTRIBUTION;
        } else {
            throw new ModuleComposerException("""
                    No module selection was provided.

                    Examples:
                      ./gradlew bundleRun -Pmodules=payment
                      ./gradlew bundleRun -Pmodules=payment,notification
                      ./gradlew bundleRun -Pdistribution=community
                    """);
        }

        selected.addAll(includes);
        selected.removeAll(excludes);

        if (selected.isEmpty()) {
            throw new ModuleComposerException("No modules remain after overrides.");
        }

        List<ModuleRegistration> modules = new ArrayList<>();
        for (String name : selected) {
            ModuleRegistration definition = registry.get(normalize(name));

            if (definition == null) {
                throw new ModuleComposerException(
                        "Unknown module '" + name +
                                "'. Available modules: " +
                                registry.modules().keySet()
                );
            }

            if (!projectValidator.exists(definition.projectPath())) {
                throw new ModuleComposerException(
                        "Gradle project not found for module '" + name +
                                "': " + definition.projectPath()
                );
            }

            modules.add(definition);
        }

        return new ModuleSelection(
                List.copyOf(modules),
                distribution,
                request.runtimeOptions(),
                mode
        );
    }

    private List<String> normalizeAll(List<String> values) {
        if (values == null) {
            return List.of();
        }

        return values.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalize)
                .distinct()
                .toList();
    }

    private String normalize(String name) {
        return name.trim().replaceFirst("^module-", "");
    }

    public interface ProjectValidator {
        boolean exists(String projectPath);
    }
}
