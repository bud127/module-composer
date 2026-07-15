package io.github.bud127.modulecomposer.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ModuleSelector {

    public static final String DEFAULT_APPLICATION_NAME = "combined-app";

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
        String applicationName = requestedApplicationName;
        DistributionArtifact artifact = null;
        DistributionContainer container = null;

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
            DistributionConfig config = distributionLoader.loadRequired(distribution);
            DistributionPreset preset =
                    config.distributions().get(distribution);

            if (preset == null) {
                throw new ModuleComposerException(
                        "Unknown distribution '" + distribution +
                                "'. Available distributions: " +
                                config.distributions().keySet()
                );
            }

            selected.addAll(normalizeAll(preset.modules()));
            if (applicationName == null) {
                applicationName = normalizeApplicationName(
                        preset.applicationName(),
                        "distribution '" + distribution + "' applicationName"
                );
            }
            artifact = normalizeArtifact(preset.artifact(), distribution);
            container = normalizeContainer(preset.container(), distribution);
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
                applicationName == null
                        ? DEFAULT_APPLICATION_NAME
                        : applicationName,
                artifact,
                container,
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

        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new ModuleComposerException(
                    "Invalid application name '" + value + "' from " + source +
                            ". Use letters, numbers, dots, underscores, and dashes; start with a letter or number."
            );
        }

        return normalized;
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

        String image = container.image() == null ? null : container.image().trim();
        String baseImage = container.baseImage() == null
                ? null
                : container.baseImage().trim();
        if ((image == null || image.isBlank())
                && (baseImage == null || baseImage.isBlank())
                && hostPort == null
                && containerPort == null) {
            return null;
        }

        return new DistributionContainer(
                image == null || image.isBlank() ? null : image,
                baseImage == null || baseImage.isBlank() ? null : baseImage,
                hostPort,
                containerPort
        );
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
}
