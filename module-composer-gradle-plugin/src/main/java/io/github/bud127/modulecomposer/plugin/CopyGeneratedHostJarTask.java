package io.github.bud127.modulecomposer.plugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public abstract class CopyGeneratedHostJarTask extends DefaultTask {

    private static final String DEFAULT_BASE_IMAGE = "eclipse-temurin:21-jre";
    private static final int DEFAULT_PORT = 8080;

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSourceJar();

    @OutputFile
    public abstract RegularFileProperty getTargetJar();

    @Input
    public abstract Property<String> getApplicationName();

    @Input
    public abstract Property<Boolean> getContainerEnabled();

    @OutputDirectory
    public abstract DirectoryProperty getContainerDirectory();

    @Input
    @Optional
    public abstract Property<String> getContainerImage();

    @Input
    @Optional
    public abstract Property<String> getContainerBaseImage();

    @Input
    @Optional
    public abstract Property<Integer> getContainerHostPort();

    @Input
    @Optional
    public abstract Property<Integer> getContainerPort();

    @TaskAction
    public void copyJar() throws IOException {
        Path source = getSourceJar().get().getAsFile().toPath();
        Path target = getTargetJar().get().getAsFile().toPath();

        if (!Files.exists(source)) {
            throw new IOException("Generated JAR not found: " + source);
        }

        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        writeContainerFiles(target);
        getLogger().lifecycle("Combined JAR: {}", target);
    }

    private void writeContainerFiles(Path target) throws IOException {
        Path directory = getContainerDirectory().get().getAsFile().toPath();
        if (!getContainerEnabled().get()) {
            deleteContainerFiles(directory);
            return;
        }

        Files.createDirectories(directory);
        String serviceName = containerServiceName(getApplicationName().get());
        int hostPort = containerHostPort();
        int containerPort = containerContainerPort();
        String jarReference = target.getFileName().toString();

        Files.writeString(
                directory.resolve("Dockerfile"),
                """
                FROM %s

                WORKDIR /app

                ARG JAR_FILE=%s
                COPY ${JAR_FILE} /app/app.jar

                EXPOSE %d

                ENTRYPOINT ["java", "-jar", "/app/app.jar"]
                """.formatted(containerBaseImage(), jarReference, containerPort)
        );

        Files.writeString(
                directory.resolve("docker-compose.yml"),
                """
                services:
                  %s:
                    build:
                      context: ../..
                      dockerfile: containers/%s/Dockerfile
                      args:
                        JAR_FILE: %s
                    image: %s
                    ports:
                      - "%d:%d"
                    restart: unless-stopped
                """.formatted(
                        serviceName,
                        serviceName,
                        jarReference,
                        containerImage(),
                        hostPort,
                        containerPort
                )
        );
    }

    private static void deleteContainerFiles(Path directory) throws IOException {
        Files.deleteIfExists(directory.resolve("Dockerfile"));
        Files.deleteIfExists(directory.resolve("docker-compose.yml"));
        Files.deleteIfExists(directory);
    }

    private String containerImage() {
        String image = getContainerImage().getOrNull();
        return image == null ? getApplicationName().get() + ":local" : image;
    }

    private String containerBaseImage() {
        String baseImage = getContainerBaseImage().getOrNull();
        return baseImage == null ? DEFAULT_BASE_IMAGE : baseImage;
    }

    private int containerHostPort() {
        Integer hostPort = getContainerHostPort().getOrNull();
        if (hostPort != null) {
            return hostPort;
        }
        Integer containerPort = getContainerPort().getOrNull();
        return containerPort == null ? DEFAULT_PORT : containerPort;
    }

    private int containerContainerPort() {
        Integer containerPort = getContainerPort().getOrNull();
        if (containerPort != null) {
            return containerPort;
        }
        Integer hostPort = getContainerHostPort().getOrNull();
        return hostPort == null ? DEFAULT_PORT : hostPort;
    }

    static String containerServiceName(String value) {
        StringBuilder normalized = new StringBuilder();
        boolean pendingSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char character = Character.toLowerCase(value.charAt(index));
            if (isContainerServiceNameCharacter(character)) {
                appendPendingSeparator(normalized, pendingSeparator);
                normalized.append(character);
                pendingSeparator = false;
            } else {
                pendingSeparator = !normalized.isEmpty();
            }
        }
        return normalized.isEmpty() ? "app" : normalized.toString();
    }

    private static void appendPendingSeparator(
            StringBuilder value,
            boolean pendingSeparator
    ) {
        if (pendingSeparator) {
            value.append('-');
        }
    }

    private static boolean isContainerServiceNameCharacter(char value) {
        return value >= 'a' && value <= 'z'
                || value >= '0' && value <= '9'
                || value == '_'
                || value == '-';
    }
}
