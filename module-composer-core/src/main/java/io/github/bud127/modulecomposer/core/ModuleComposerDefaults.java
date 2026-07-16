package io.github.bud127.modulecomposer.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class ModuleComposerDefaults {

    public static final String DEFAULT_APPLICATION_NAME = "combined-app";
    public static final String DEFAULT_FRAMEWORK_ID = "spring-boot";
    public static final String DEFAULT_STANDALONE_RUN_TASK = "bootRun";
    public static final String DEFAULT_STANDALONE_BUILD_TASK = "bootJar";
    public static final String DEFAULT_PLAIN_JAR_TASK = "jar";
    public static final String MODULE_PROJECT_PREFIX = "module-";
    public static final String SPRING_BOOT_VERSION_KEY = "springBootVersion";
    public static final String DEPENDENCY_MANAGEMENT_VERSION_KEY =
            "dependencyManagementVersion";
    public static final String RUNTIME_PORT_PROPERTY = "port";

    private static final Properties PROPERTIES = loadProperties();

    public static final String SPRING_BOOT_VERSION =
            required(SPRING_BOOT_VERSION_KEY);
    public static final String DEPENDENCY_MANAGEMENT_VERSION =
            required(DEPENDENCY_MANAGEMENT_VERSION_KEY);
    public static final int JAVA_VERSION = 21;

    private ModuleComposerDefaults() {
    }

    private static Properties loadProperties() {
        try (InputStream stream = ModuleComposerDefaults.class
                .getClassLoader()
                .getResourceAsStream("module-composer-defaults.properties")) {
            if (stream == null) {
                throw new IllegalStateException(
                        "Missing module-composer-defaults.properties"
                );
            }
            Properties properties = new Properties();
            properties.load(stream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to load module-composer defaults",
                    exception
            );
        }
    }

    private static String required(String key) {
        String value = PROPERTIES.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing Module Composer default '" + key + "'"
            );
        }
        return value;
    }
}
