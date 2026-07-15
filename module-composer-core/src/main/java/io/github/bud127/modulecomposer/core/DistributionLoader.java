package io.github.bud127.modulecomposer.core;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DistributionLoader {

    private static final String DISTRIBUTIONS_KEY = "distributions";
    private static final String YAML_EXTENSION = ".yaml";
    private static final String YML_EXTENSION = ".yml";
    private static final String INVALID_YAML_ROOT_MESSAGE = "Invalid YAML root in ";
    private static final String UNABLE_TO_READ_MESSAGE = "Unable to read ";

    private final Path file;

    public DistributionLoader(Path file) {
        this.file = file;
    }

    public boolean exists() {
        return Files.exists(file) || Files.exists(defaultDistributionDirectory());
    }

    public DistributionConfig loadRequired() {
        Path source = requiredDistributionSource();
        return Files.isDirectory(source)
                ? new DistributionLoader(source).loadDirectory()
                : parseDistributionFile(source);
    }

    private Path requiredDistributionSource() {
        if (Files.exists(file)) {
            return file;
        }

        Path defaultDirectory = defaultDistributionDirectory();
        if (Files.isDirectory(defaultDirectory)) {
            return defaultDirectory;
        }

        throw new ModuleComposerException(
                "Distribution preset was requested, but no distribution file was found at " +
                        file.toAbsolutePath() +
                        ". Distribution presets are optional for -Pmodules, but required for -Pdistribution."
        );
    }

    private DistributionConfig parseDistributionFile(Path path) {
        try {
            Object parsed = new Yaml().load(Files.readString(path));
            Map<?, ?> root = yamlRoot(parsed, path);
            return parseDistributionRoot(root);
        } catch (IOException exception) {
            throw new ModuleComposerException(UNABLE_TO_READ_MESSAGE + path, exception);
        }
    }

    private static Map<?, ?> yamlRoot(Object parsed, Path path) {
        if (parsed instanceof Map<?, ?> root) {
            return root;
        }

        throw new ModuleComposerException(INVALID_YAML_ROOT_MESSAGE + path);
    }

    private DistributionConfig parseDistributionRoot(Map<?, ?> root) {
        if (root.get(DISTRIBUTIONS_KEY) instanceof Map<?, ?> distributionMap) {
            return parseMultiDistributionFile(distributionMap);
        }

        NamedDistribution distribution = parseSingleDistributionFile(root);
        return new DistributionConfig(Map.of(distribution.name(), distribution.preset()));
    }

    public DistributionConfig loadRequired(String distributionName) {
        if (Files.isDirectory(file)) {
            return parseSingleDistributionPath(namedDistributionFile(file, distributionName));
        }

        if (Files.exists(file)) {
            DistributionConfig config = loadRequired();
            if (config.distributions().containsKey(distributionName)) {
                return config;
            }

            Path named = namedDistributionFile(
                    defaultDistributionDirectory(),
                    distributionName
            );
            if (Files.exists(named)) {
                Map<String, DistributionPreset> distributions =
                        new LinkedHashMap<>(config.distributions());
                distributions.putAll(parseSingleDistributionPath(named).distributions());
                return new DistributionConfig(distributions);
            }

            return config;
        }

        Path named = namedDistributionFile(
                defaultDistributionDirectory(),
                distributionName
        );
        if (Files.exists(named)) {
            return parseSingleDistributionPath(named);
        }

        return loadRequired();
    }

    private DistributionConfig loadDirectory() {
        try (var paths = Files.list(file)) {
            Map<String, DistributionPreset> distributions = new LinkedHashMap<>();
            paths.filter(path -> path.getFileName().toString().endsWith(YAML_EXTENSION)
                            || path.getFileName().toString().endsWith(YML_EXTENSION))
                    .map(this::parseSingleDistributionPath)
                    .flatMap(config -> config.distributions().entrySet().stream())
                    .forEach(entry -> distributions.put(entry.getKey(), entry.getValue()));

            return new DistributionConfig(distributions);
        } catch (IOException exception) {
            throw new ModuleComposerException(UNABLE_TO_READ_MESSAGE + file, exception);
        }
    }

    private DistributionConfig parseMultiDistributionFile(Map<?, ?> distributionMap) {
        Map<String, DistributionPreset> distributions = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : distributionMap.entrySet()) {
            String name = String.valueOf(entry.getKey());

            if (!(entry.getValue() instanceof Map<?, ?> metadata)) {
                throw new ModuleComposerException(
                        "Distribution '" + name + "' must define metadata."
                );
            }

            distributions.put(name, parsePreset(name, metadata));
        }

        return new DistributionConfig(distributions);
    }

    private DistributionConfig parseSingleDistributionPath(Path path) {
        if (!Files.exists(path)) {
            throw new ModuleComposerException(
                    "Distribution file was not found at " + path.toAbsolutePath()
            );
        }

        try {
            Object parsed = new Yaml().load(Files.readString(path));
            Map<?, ?> root = yamlRoot(parsed, path);
            NamedDistribution distribution = parseSingleDistributionFile(root);
            return new DistributionConfig(Map.of(distribution.name(), distribution.preset()));
        } catch (IOException exception) {
            throw new ModuleComposerException(UNABLE_TO_READ_MESSAGE + path, exception);
        }
    }

    private NamedDistribution parseSingleDistributionFile(Map<?, ?> root) {
        String name = optionalString(root.get("name"));
        if (name == null) {
            throw new ModuleComposerException(
                    "Single distribution YAML must define a non-empty 'name'."
            );
        }

        return new NamedDistribution(name, parsePreset(name, root));
    }

    private DistributionPreset parsePreset(String name, Map<?, ?> metadata) {
        if (!(metadata.get("modules") instanceof List<?> list)) {
            throw new ModuleComposerException(
                    "Distribution '" + name + "' must define a modules list."
            );
        }

        String applicationName = optionalString(metadata.get("applicationName"));
        if (applicationName == null) {
            applicationName = optionalString(metadata.get("name"));
        }

        return new DistributionPreset(
                list.stream().map(String::valueOf).toList(),
                applicationName,
                parseArtifact(metadata.get("artifact")),
                parseContainer(metadata.get("container"))
        );
    }

    private DistributionArtifact parseArtifact(Object value) {
        if (!(value instanceof Map<?, ?> metadata)) {
            return null;
        }

        String fileName = optionalString(metadata.get("fileName"));
        return fileName == null ? null : new DistributionArtifact(fileName);
    }

    private DistributionContainer parseContainer(Object value) {
        if (!(value instanceof Map<?, ?> metadata)) {
            return null;
        }

        String image = optionalString(metadata.get("image"));
        String baseImage = optionalString(metadata.get("baseImage"));
        Integer hostPort = optionalContainerPort(metadata, "hostPort");
        Integer containerPort = optionalContainerPort(metadata, "containerPort");
        if (!hasContainerMetadata(image, baseImage, hostPort, containerPort)) {
            return null;
        }

        return new DistributionContainer(
                image,
                baseImage,
                hostPort,
                containerPort
        );
    }

    private static Integer optionalContainerPort(
            Map<?, ?> metadata,
            String name
    ) {
        return optionalInteger(metadata.get(name), "container." + name);
    }

    private static boolean hasContainerMetadata(
            String image,
            String baseImage,
            Integer hostPort,
            Integer containerPort
    ) {
        return image != null
                || baseImage != null
                || hostPort != null
                || containerPort != null;
    }

    private static Integer optionalInteger(Object value, String field) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            throw new ModuleComposerException(field + " must be an integer.");
        }
    }

    private static String optionalString(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }

    private static Path namedDistributionFile(Path directory, String distributionName) {
        Path yaml = directory.resolve(distributionName + YAML_EXTENSION);
        if (Files.exists(yaml)) {
            return yaml;
        }

        return directory.resolve(distributionName + YML_EXTENSION);
    }

    private Path defaultDistributionDirectory() {
        Path parent = file.getParent() == null ? Path.of(".") : file.getParent();
        return parent.resolve(DISTRIBUTIONS_KEY);
    }

    private record NamedDistribution(
            String name,
            DistributionPreset preset
    ) {
    }
}
