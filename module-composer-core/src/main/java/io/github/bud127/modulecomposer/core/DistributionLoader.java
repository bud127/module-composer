package io.github.bud127.modulecomposer.core;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DistributionLoader {

    private final Path file;

    public DistributionLoader(Path file) {
        this.file = file;
    }

    public boolean exists() {
        return Files.exists(file) || Files.exists(defaultDistributionDirectory());
    }

    public DistributionConfig loadRequired() {
        if (Files.isDirectory(file)) {
            return loadDirectory();
        }

        if (!Files.exists(file) && Files.isDirectory(defaultDistributionDirectory())) {
            return new DistributionLoader(defaultDistributionDirectory()).loadRequired();
        }

        if (!Files.exists(file)) {
            throw new ModuleComposerException(
                    "Distribution preset was requested, but no distribution file was found at " +
                            file.toAbsolutePath() +
                            ". Distribution presets are optional for -Pmodules, but required for -Pdistribution."
            );
        }

        try {
            Object parsed = new Yaml().load(Files.readString(file));

            if (!(parsed instanceof Map<?, ?> root)) {
                throw new ModuleComposerException("Invalid YAML root in " + file);
            }

            if (root.get("distributions") instanceof Map<?, ?> distributionMap) {
                return parseMultiDistributionFile(distributionMap);
            }

            NamedDistribution distribution = parseSingleDistributionFile(root);
            return new DistributionConfig(Map.of(distribution.name(), distribution.preset()));
        } catch (IOException exception) {
            throw new ModuleComposerException("Unable to read " + file, exception);
        }
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
            paths.filter(path -> path.getFileName().toString().endsWith(".yaml")
                            || path.getFileName().toString().endsWith(".yml"))
                    .map(this::parseSingleDistributionPath)
                    .flatMap(config -> config.distributions().entrySet().stream())
                    .forEach(entry -> distributions.put(entry.getKey(), entry.getValue()));

            return new DistributionConfig(distributions);
        } catch (IOException exception) {
            throw new ModuleComposerException("Unable to read " + file, exception);
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

            if (!(parsed instanceof Map<?, ?> root)) {
                throw new ModuleComposerException("Invalid YAML root in " + path);
            }

            NamedDistribution distribution = parseSingleDistributionFile(root);
            return new DistributionConfig(Map.of(distribution.name(), distribution.preset()));
        } catch (IOException exception) {
            throw new ModuleComposerException("Unable to read " + path, exception);
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
        Integer hostPort = optionalInteger(
                metadata.get("hostPort"),
                "container.hostPort"
        );
        Integer containerPort = optionalInteger(
                metadata.get("containerPort"),
                "container.containerPort"
        );
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
        Path yaml = directory.resolve(distributionName + ".yaml");
        if (Files.exists(yaml)) {
            return yaml;
        }

        return directory.resolve(distributionName + ".yml");
    }

    private Path defaultDistributionDirectory() {
        Path parent = file.getParent() == null ? Path.of(".") : file.getParent();
        return parent.resolve("distributions");
    }

    private record NamedDistribution(
            String name,
            DistributionPreset preset
    ) {
    }
}
