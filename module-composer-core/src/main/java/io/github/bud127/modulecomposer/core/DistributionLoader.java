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
        return Files.exists(file);
    }

    public DistributionConfig loadRequired() {
        if (!Files.exists(file)) {
            throw new ModuleComposerException(
                    "Distribution preset was requested, but no distribution file" +
                            " file was found at " +
                            file.toAbsolutePath() +
                            ". Distribution presets are optional for -Pmodules, but required for -Pdistribution."
            );
        }

        try {
            Object parsed = new Yaml().load(Files.readString(file));

            if (!(parsed instanceof Map<?, ?> root)) {
                throw new ModuleComposerException("Invalid YAML root in " + file);
            }

            if (!(root.get("distributions") instanceof Map<?, ?> distributionMap)) {
                throw new ModuleComposerException("Missing 'distributions' section in " + file);
            }

            Map<String, DistributionPreset> distributions = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : distributionMap.entrySet()) {
                String name = String.valueOf(entry.getKey());

                if (!(entry.getValue() instanceof Map<?, ?> metadata)
                        || !(metadata.get("modules") instanceof List<?> list)) {
                    throw new ModuleComposerException(
                            "Distribution '" + name + "' must define a modules list."
                    );
                }

                String applicationName = optionalString(metadata.get("applicationName"));
                distributions.put(
                        name,
                        new DistributionPreset(
                                list.stream().map(String::valueOf).toList(),
                                applicationName
                        )
                );
            }

            return new DistributionConfig(distributions);
        } catch (IOException exception) {
            throw new ModuleComposerException("Unable to read " + file, exception);
        }
    }

    private static String optionalString(Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value).trim();
        return text.isBlank() ? null : text;
    }
}
