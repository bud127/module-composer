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
        String requestedApplicationName = normalizeApplicationName(
                request.applicationName(),
                "-PapplicationName"
        );

        validateSelectionSource(cliModules, distribution);
        ResolvedSelection resolved = resolveBaseSelection(
                cliModules,
                distribution,
                requestedApplicationName
        );
        LinkedHashSet<String> selected = applyOverrides(
                resolved.moduleNames(),
                includes,
                excludes
        );
        List<ModuleRegistration> modules = resolveModules(selected);

        return new ModuleSelection(
                List.copyOf(modules),
                distribution,
                resolved.applicationName() == null
                        ? ModuleComposerDefaults.DEFAULT_APPLICATION_NAME
                        : resolved.applicationName(),
                resolved.artifact(),
                resolved.container(),
                request.runtimeOptions(),
                resolved.mode()
        );
    }

    private void validateSelectionSource(
            List<String> cliModules,
            String distribution
    ) {
        if (!cliModules.isEmpty() && hasText(distribution)) {
            throw new ModuleComposerException(
                    "Module selection is ambiguous. Use either -Pmodules or -Pdistribution, not both."
            );
        }
    }

    private ResolvedSelection resolveBaseSelection(
            List<String> cliModules,
            String distribution,
            String requestedApplicationName
    ) {
        if (!cliModules.isEmpty()) {
            return new ResolvedSelection(
                    new LinkedHashSet<>(cliModules),
                    requestedApplicationName,
                    null,
                    null,
                    SelectionMode.CLI
            );
        }

        if (hasText(distribution)) {
            return resolveDistributionSelection(
                    distribution,
                    requestedApplicationName
            );
        }

        throw new ModuleComposerException("""
                No module selection was provided.

                Examples:
                  ./gradlew bundleRun -Pmodules=payment
                  ./gradlew bundleRun -Pmodules=payment,notification
                  ./gradlew bundleRun -Pdistribution=community
                """);
    }

    private ResolvedSelection resolveDistributionSelection(
            String distribution,
            String requestedApplicationName
    ) {
        DistributionConfig config = distributionLoader.loadRequired(distribution);
        DistributionPreset preset = config.distributions().get(distribution);

        if (preset == null) {
            throw new ModuleComposerException(
                    "Unknown distribution '" + distribution +
                            "'. Available distributions: " +
                            config.distributions().keySet()
            );
        }

        String applicationName = requestedApplicationName == null
                ? normalizeApplicationName(
                        preset.applicationName(),
                        "distribution '" + distribution + "' applicationName"
                )
                : requestedApplicationName;

        return new ResolvedSelection(
                new LinkedHashSet<>(normalizeAll(preset.modules())),
                applicationName,
                normalizeArtifact(preset.artifact(), distribution),
                normalizeContainer(preset.container(), distribution),
                SelectionMode.DISTRIBUTION
        );
    }

    private static LinkedHashSet<String> applyOverrides(
            LinkedHashSet<String> baseSelection,
            List<String> includes,
            Set<String> excludes
    ) {
        LinkedHashSet<String> selected = new LinkedHashSet<>(baseSelection);
        selected.addAll(includes);
        selected.removeAll(excludes);

        if (selected.isEmpty()) {
            throw new ModuleComposerException("No modules remain after overrides.");
        }

        return selected;
    }

    private List<ModuleRegistration> resolveModules(
            LinkedHashSet<String> selected
    ) {
        List<ModuleRegistration> modules = new ArrayList<>();
        for (String name : selected) {
            modules.add(resolveModule(name));
        }
        return modules;
    }

    private ModuleRegistration resolveModule(String name) {
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

        return definition;
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
        String normalized = name.trim();
        if (normalized.startsWith(ModuleComposerDefaults.MODULE_PROJECT_PREFIX)) {
            return normalized.substring(
                    ModuleComposerDefaults.MODULE_PROJECT_PREFIX.length()
            );
        }
        return normalized;
    }

    private static String normalizeApplicationName(
            String value,
            String source
    ) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }

        if (!isApplicationName(normalized)) {
            throw new ModuleComposerException(
                    "Invalid application name '" + value + "' from " + source +
                            ". Use letters, numbers, dots, underscores, and dashes; start with a letter or number."
            );
        }

        return normalized;
    }

    private static boolean isApplicationName(String value) {
        if (!isAsciiLetterOrDigit(value.charAt(0))) {
            return false;
        }

        for (int index = 1; index < value.length(); index++) {
            if (!isApplicationNamePart(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isApplicationNamePart(char value) {
        return isAsciiLetterOrDigit(value)
                || value == '.'
                || value == '_'
                || value == '-';
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9';
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static DistributionArtifact normalizeArtifact(
            DistributionArtifact artifact,
            String distribution
    ) {
        if (artifact == null || artifact.fileName() == null) {
            return null;
        }

        String fileName = artifact.fileName().trim();
        if (fileName.isBlank()) {
            return null;
        }

        if (fileName.contains("/") || fileName.contains("\\")) {
            throw new ModuleComposerException(
                    "Invalid artifact fileName '" + artifact.fileName() +
                            "' from distribution '" + distribution +
                            "'. Use a file name only, not a path."
            );
        }

        return new DistributionArtifact(fileName);
    }

    private static DistributionContainer normalizeContainer(
            DistributionContainer container,
            String distribution
    ) {
        if (container == null) {
            return null;
        }

        Integer hostPort = normalizePort(
                container.hostPort(),
                "container hostPort",
                distribution
        );
        Integer containerPort = normalizePort(
                container.containerPort(),
                "container containerPort",
                distribution
        );

        String image = normalizeOptionalText(container.image());
        String baseImage = normalizeOptionalText(container.baseImage());
        if (image == null
                && baseImage == null
                && hostPort == null
                && containerPort == null) {
            return null;
        }

        return new DistributionContainer(
                image,
                baseImage,
                hostPort,
                containerPort
        );
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }

    private static Integer normalizePort(
            Integer port,
            String field,
            String distribution
    ) {
        if (port != null && (port < 1 || port > 65535)) {
            throw new ModuleComposerException(
                    "Invalid " + field + " '" + port +
                            "' from distribution '" + distribution +
                            "'. Port must be between 1 and 65535."
            );
        }

        return port;
    }

    public interface ProjectValidator {
        boolean exists(String projectPath);
    }

    private record ResolvedSelection(
            LinkedHashSet<String> moduleNames,
            String applicationName,
            DistributionArtifact artifact,
            DistributionContainer container,
            SelectionMode mode
    ) {
    }
}
