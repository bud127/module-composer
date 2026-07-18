package io.github.bud127.modulecomposer.quarkus;

import io.github.bud127.modulecomposer.core.CompositionPlan;
import io.github.bud127.modulecomposer.core.FrameworkAdapter;
import io.github.bud127.modulecomposer.core.GeneratedHostContext;
import io.github.bud127.modulecomposer.core.ModuleComposerDefaults;
import io.github.bud127.modulecomposer.core.ModuleRegistration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class QuarkusFrameworkAdapter implements FrameworkAdapter {

    public static final String ID = "quarkus";

    private final String quarkusVersion;

    public QuarkusFrameworkAdapter() {
        this(ModuleComposerDefaults.QUARKUS_VERSION);
    }

    public QuarkusFrameworkAdapter(String quarkusVersion) {
        this.quarkusVersion = quarkusVersion;
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
        new QuarkusGeneratedHostFactory(
                context.frameworkOptions()
                        .getOrDefault(
                                ModuleComposerDefaults.QUARKUS_VERSION_KEY,
                                quarkusVersion
                        )
        ).generate(context);
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
        return "quarkusDev";
    }

    @Override
    public String generatedBuildTask() {
        return "quarkusBuild";
    }

    @Override
    public Path generatedArtifact() {
        return Path.of("build/combined-app-runner.jar");
    }

    static final class QuarkusGeneratedHostFactory {

        private static final String APPLICATION_PACKAGE = "com.bysa.generated";
        private final String quarkusVersion;

        QuarkusGeneratedHostFactory(String quarkusVersion) {
            this.quarkusVersion = quarkusVersion;
        }

        void generate(GeneratedHostContext context) throws IOException {
            Path host = context.hostDirectory();
            deleteDirectory(host);

            Path javaDirectory = host.resolve(
                    "src/main/java/" + APPLICATION_PACKAGE.replace('.', '/')
            );
            Path testDirectory = host.resolve(
                    "src/test/java/" + APPLICATION_PACKAGE.replace('.', '/')
            );
            Path resourcesDirectory = host.resolve("src/main/resources");

            Files.createDirectories(javaDirectory);
            Files.createDirectories(testDirectory);
            Files.createDirectories(resourcesDirectory);

            writeSettings(host);
            writeBuild(host, context);
            writeApplication(javaDirectory, context);
            writeResourceLocator(javaDirectory, context);
            writeApplicationTest(testDirectory);
            writeResources(resourcesDirectory, context);
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
                        id("io.quarkus") version "%s"
                    }

                    version = "1.0.0"

                    java {
                        toolchain {
                            languageVersion.set(JavaLanguageVersion.of(%d))
                        }
                    }

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:%s"))
                        implementation("io.quarkus:quarkus-rest-jackson")
                        implementation("io.quarkus:quarkus-smallrye-health")
                        testImplementation("io.quarkus:quarkus-junit5")
                    %s
                    }

                    fun controlValue(key: String): String? =
                        providers.gradleProperty(key).orNull
                            ?: gradle.startParameter.systemPropertiesArgs[key]

                    controlValue("%s")
                        ?.takeIf { it.isNotBlank() }
                        ?.let { System.setProperty("quarkus.http.port", it) }

                    tasks.named<Test>("test") {
                        useJUnitPlatform()
                    }
                    """.formatted(
                            quarkusVersion,
                            context.javaVersion(),
                            quarkusVersion,
                            dependencies,
                            ModuleComposerDefaults.RUNTIME_PORT_PROPERTY
                    )
            );
        }

        private void writeApplication(
                Path javaDirectory,
                GeneratedHostContext context
        ) throws IOException {
            Files.writeString(
                    javaDirectory.resolve("GeneratedCombinedApplication.java"),
                    """
                    package com.bysa.generated;

                    import io.quarkus.runtime.Quarkus;
                    import io.quarkus.runtime.annotations.QuarkusMain;

                    @QuarkusMain
                    public class GeneratedCombinedApplication {

                        public static void main(String[] args) {
                            Quarkus.run(args);
                        }
                    }
                    """
            );
        }

        private void writeResourceLocator(
                Path javaDirectory,
                GeneratedHostContext context
        ) throws IOException {
            List<String> configurations = context.configurationClasses();

            String imports = configurations.stream()
                    .map(value -> "import " + value + ";")
                    .distinct()
                    .reduce("", (left, right) ->
                            left + right + System.lineSeparator()
                    );

            String locators = "";
            for (int index = 0; index < configurations.size(); index++) {
                String moduleName = index < context.moduleNames().size()
                        ? context.moduleNames().get(index)
                        : "module-" + index;
                String className = configurations.get(index);
                locators += """

                    @GET
                    @Path("/api/%s/{path:.*}")
                    public Object %s(@PathParam("path") String path) {
                        return invokeGet(new %s(), path);
                    }
                    """.formatted(
                        moduleName,
                        methodName(moduleName, index),
                        simpleName(className)
                );
            }

            Files.writeString(
                    javaDirectory.resolve("GeneratedModuleResourceLocator.java"),
                    """
                    package com.bysa.generated;

                    %s
                    import jakarta.ws.rs.GET;
                    import jakarta.ws.rs.NotFoundException;
                    import jakarta.ws.rs.Path;
                    import jakarta.ws.rs.PathParam;
                    import jakarta.ws.rs.Produces;
                    import jakarta.ws.rs.core.MediaType;

                    import java.lang.reflect.InvocationTargetException;
                    import java.lang.reflect.Method;

                    @Path("/")
                    @Produces(MediaType.APPLICATION_JSON)
                    public class GeneratedModuleResourceLocator {
                    %s
                        private static Object invokeGet(Object resource, String path) {
                            String requestedPath = normalize(path);
                            for (Method method : resource.getClass().getMethods()) {
                                if (method.getParameterCount() != 0
                                        || method.getAnnotation(GET.class) == null
                                        || !normalize(path(method)).equals(requestedPath)) {
                                    continue;
                                }
                                try {
                                    return method.invoke(resource);
                                } catch (IllegalAccessException exception) {
                                    throw new IllegalStateException(exception);
                                } catch (InvocationTargetException exception) {
                                    Throwable cause = exception.getCause();
                                    if (cause instanceof RuntimeException runtimeException) {
                                        throw runtimeException;
                                    }
                                    throw new IllegalStateException(cause);
                                }
                            }
                            throw new NotFoundException();
                        }

                        private static String path(Method method) {
                            Path path = method.getAnnotation(Path.class);
                            return path == null ? "" : path.value();
                        }

                        private static String normalize(String path) {
                            if (path == null || path.isBlank()) {
                                return "";
                            }
                            String normalized = path;
                            while (normalized.startsWith("/")) {
                                normalized = normalized.substring(1);
                            }
                            while (normalized.endsWith("/")) {
                                normalized = normalized.substring(0, normalized.length() - 1);
                            }
                            return normalized;
                        }
                    }
                    """.formatted(
                            imports,
                            locators
                    )
            );
        }

        private void writeApplicationTest(Path testDirectory) throws IOException {
            Files.writeString(
                    testDirectory.resolve("GeneratedCombinedApplicationTest.java"),
                    """
                    package com.bysa.generated;

                    import static org.junit.jupiter.api.Assertions.assertNotNull;

                    import io.quarkus.test.junit.QuarkusTest;
                    import org.junit.jupiter.api.Test;

                    @QuarkusTest
                    class GeneratedCombinedApplicationTest {

                        @Test
                        void contextLoads() {
                            assertNotNull(GeneratedCombinedApplication.class);
                        }
                    }
                    """
            );
        }

        private void writeResources(
                Path resourcesDirectory,
                GeneratedHostContext context
        ) throws IOException {
            Files.writeString(
                    resourcesDirectory.resolve("application.properties"),
                    """
                    quarkus.application.name=%s
                    quarkus.http.port=8080
                    quarkus.package.jar.type=uber-jar
                    quarkus.package.output-name=combined-app
                    composer.modules=%s
                    composer.distribution=%s
                    composer.configuration-classes=%s
                    """.formatted(
                            context.applicationName(),
                            String.join(",", context.moduleNames()),
                            context.distribution() == null ? "" : context.distribution(),
                            String.join(",", context.configurationClasses())
                    )
            );
        }

        private static String quote(String value) {
            return "\"" + value.replace("\\", "\\\\") + "\"";
        }

        private static String simpleName(String className) {
            int index = className.lastIndexOf('.');
            return index >= 0 ? className.substring(index + 1) : className;
        }

        private static String methodName(String moduleName, int index) {
            StringBuilder method = new StringBuilder("module");
            boolean uppercaseNext = true;
            for (int charIndex = 0; charIndex < moduleName.length(); charIndex++) {
                char value = moduleName.charAt(charIndex);
                if (Character.isLetterOrDigit(value)) {
                    method.append(
                            uppercaseNext
                                    ? Character.toUpperCase(value)
                                    : value
                    );
                    uppercaseNext = false;
                } else {
                    uppercaseNext = true;
                }
            }
            if (method.length() == "module".length()) {
                method.append(index);
            }
            return method.toString();
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
