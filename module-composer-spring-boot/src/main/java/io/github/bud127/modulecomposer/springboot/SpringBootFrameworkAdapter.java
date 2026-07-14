package io.github.bud127.modulecomposer.springboot;

import io.github.bud127.modulecomposer.core.CompositionPlan;
import io.github.bud127.modulecomposer.core.FrameworkAdapter;
import io.github.bud127.modulecomposer.core.GeneratedHostContext;
import io.github.bud127.modulecomposer.core.ModuleRegistration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class SpringBootFrameworkAdapter implements FrameworkAdapter {

    public static final String ID = "spring-boot";

    private final String springBootVersion;
    private final String dependencyManagementVersion;

    public SpringBootFrameworkAdapter() {
        this("3.5.7", "1.1.7");
    }

    public SpringBootFrameworkAdapter(
            String springBootVersion,
            String dependencyManagementVersion
    ) {
        this.springBootVersion = springBootVersion;
        this.dependencyManagementVersion = dependencyManagementVersion;
    }

    @Override
    public String frameworkId() {
        return ID;
    }

    @Override
    public void generateHost(
            CompositionPlan plan,
            GeneratedHostContext context
    ) throws IOException {
        SpringBootGeneratedHostFactory factory =
                new SpringBootGeneratedHostFactory(
                        context.frameworkOptions()
                                .getOrDefault("springBootVersion", springBootVersion),
                        context.frameworkOptions()
                                .getOrDefault("dependencyManagementVersion", dependencyManagementVersion)
                );
        factory.generate(plan, context);
    }

    @Override
    public String standaloneRunTask(ModuleRegistration module) {
        return module.standaloneRunTask();
    }

    @Override
    public String standaloneBuildTask(ModuleRegistration module) {
        return module.standaloneBuildTask();
    }

    @Override
    public String generatedRunTask() {
        return "bootRun";
    }

    @Override
    public String generatedBuildTask() {
        return "bootJar";
    }

    @Override
    public Path generatedArtifact() {
        return Path.of("build/libs/combined-app.jar");
    }

    static final class SpringBootGeneratedHostFactory {

        private final String springBootVersion;
        private final String dependencyManagementVersion;

        SpringBootGeneratedHostFactory(
                String springBootVersion,
                String dependencyManagementVersion
        ) {
            this.springBootVersion = springBootVersion;
            this.dependencyManagementVersion = dependencyManagementVersion;
        }

        void generate(
                CompositionPlan plan,
                GeneratedHostContext context
        ) throws IOException {
            Path host = context.hostDirectory();
            deleteDirectory(host);

            Path javaDirectory = host.resolve(
                    "src/main/java/com/bysa/generated"
            );

            Path resourcesDirectory = host.resolve(
                    "src/main/resources"
            );

            Files.createDirectories(javaDirectory);
            Files.createDirectories(resourcesDirectory);

            writeSettings(host);
            writeBuild(host, context);
            writeApplication(javaDirectory, plan, context);
            writeResources(resourcesDirectory);
        }

        private void writeSettings(Path host) throws IOException {
            Files.writeString(
                    host.resolve("settings.gradle.kts"),
                    """
                    pluginManagement {
                        repositories {
                            gradlePluginPortal()
                            mavenCentral()
                        }
                    }

                    dependencyResolutionManagement {
                        repositories {
                            mavenCentral()
                        }
                    }

                    rootProject.name = "generated-module-composer-host"
                    """
            );
        }

        private void writeBuild(
                Path host,
                GeneratedHostContext context
        ) throws IOException {
            String dependencies = context.dependencyJarPaths()
                    .stream()
                    .map(path -> "    implementation(files(" + quote(path) + "))")
                    .reduce("", (left, right) ->
                            left + right + System.lineSeparator()
                    );

            Files.writeString(
                    host.resolve("build.gradle.kts"),
                    """
                    plugins {
                        java
                        id("org.springframework.boot") version "%s"
                        id("io.spring.dependency-management") version "%s"
                    }

                    java {
                        toolchain {
                            languageVersion.set(JavaLanguageVersion.of(%d))
                        }
                    }

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        implementation("org.springframework.boot:spring-boot-starter-web")
                        implementation("org.springframework.boot:spring-boot-starter-actuator")
                    %s
                    }

                    springBoot {
                        mainClass.set("com.bysa.generated.GeneratedCombinedApplication")
                    }

                    fun controlValue(key: String): String? =
                        providers.gradleProperty(key).orNull
                            ?: gradle.startParameter.systemPropertiesArgs[key]

                    tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
                        controlValue("port")
                            ?.takeIf { it.isNotBlank() }
                            ?.let { args("--server.port=$it") }
                    }

                    tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
                        archiveFileName.set("combined-app.jar")
                    }
                    """.formatted(
                            springBootVersion,
                            dependencyManagementVersion,
                            context.javaVersion(),
                            dependencies
                    )
            );
        }

        private void writeApplication(
                Path javaDirectory,
                CompositionPlan plan,
                GeneratedHostContext context
        ) throws IOException {
            List<String> configurations = context.configurationClasses();

            String imports = configurations.stream()
                    .map(value -> "import " + value + ";")
                    .distinct()
                    .reduce("", (left, right) ->
                            left + right + System.lineSeparator()
                    );

            String importedClasses = configurations.stream()
                    .map(value -> "        " + simpleName(value) + ".class")
                    .reduce("", (left, right) ->
                            left.isEmpty()
                                    ? right
                                    : left + "," + System.lineSeparator() + right
                    );

            String modules = String.join(",", context.moduleNames());
            String distribution = context.distribution() == null
                    ? ""
                    : context.distribution();

            Files.writeString(
                    javaDirectory.resolve("GeneratedCombinedApplication.java"),
                    """
                    package com.bysa.generated;

                    %s
                    import org.springframework.boot.SpringApplication;
                    import org.springframework.boot.autoconfigure.SpringBootApplication;
                    import org.springframework.context.annotation.Import;

                    import java.util.LinkedHashMap;
                    import java.util.Map;

                    @SpringBootApplication(scanBasePackages = "com.bysa.generated")
                    @Import({
                    %s
                    })
                    public class GeneratedCombinedApplication {

                        public static void main(String[] args) {
                            SpringApplication application =
                                    new SpringApplication(GeneratedCombinedApplication.class);

                            Map<String, Object> defaults = new LinkedHashMap<>();
                            defaults.put("spring.application.name", "combined-app");
                            defaults.put("server.port", "8080");
                            defaults.put("composer.modules", "%s");
                            defaults.put("composer.distribution", "%s");

                            application.setDefaultProperties(defaults);
                            application.run(args);
                        }
                    }
                    """.formatted(
                            imports,
                            importedClasses,
                            modules,
                            distribution
                    )
            );
        }

        private void writeResources(Path resourcesDirectory) throws IOException {
            Files.writeString(
                    resourcesDirectory.resolve("application.yml"),
                    """
                    management:
                      endpoints:
                        web:
                          exposure:
                            include:
                              - health
                              - info
                      endpoint:
                        health:
                          show-details: always
                    """
            );
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\") + "\"";
        }

        private static String simpleName(String className) {
            int index = className.lastIndexOf('.');
            return index >= 0 ? className.substring(index + 1) : className;
        }

        private static void deleteDirectory(Path directory) throws IOException {
            if (!Files.exists(directory)) {
                return;
            }

            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(
                        (left, right) ->
                                right.getNameCount() - left.getNameCount()
                ).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
