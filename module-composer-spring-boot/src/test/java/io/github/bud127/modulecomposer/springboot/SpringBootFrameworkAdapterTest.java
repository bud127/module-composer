package io.github.bud127.modulecomposer.springboot;

import io.github.bud127.modulecomposer.core.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringBootFrameworkAdapterTest {

    @TempDir
    Path directory;

    @Test
    void generatesSpringBootHostFiles() throws Exception {
        SpringBootFrameworkAdapter adapter =
                new SpringBootFrameworkAdapter();
        CompositionPlan plan = new CompositionPlan(
                "spring-boot",
                ExecutionMode.GENERATED_HOST,
                SelectionMode.CLI,
                List.of(module("payment")),
                RuntimeOptions.none(),
                new DistributionDetails(
                        null,
                        "custom-service",
                        null,
                        null
                )
        );

        adapter.generateHost(
                plan,
                new GeneratedHostContext(
                        directory,
                        new GeneratedHostClasspath(
                                List.of("/tmp/payment.jar"),
                                List.of("example.payment.PaymentConfiguration")
                        ),
                        new GeneratedHostMetadata(
                                List.of("payment"),
                                "",
                                "custom-service",
                                21,
                                Map.of()
                        )
                )
        );

        String application = Files.readString(
                directory.resolve(
                        "src/main/java/com/bysa/generated/GeneratedCombinedApplication.java"
                )
        );
        String build = Files.readString(directory.resolve("build.gradle.kts"));

        assertTrue(application.contains("@SpringBootApplication"));
        assertTrue(application.contains("PaymentConfiguration.class"));
        assertTrue(application.contains("spring.application.name\", \"custom-service"));
        assertTrue(build.contains("org.springframework.boot"));
        assertEquals("bootRun", adapter.generatedRunTask());
        assertEquals("bootJar", adapter.generatedBuildTask());
    }

    private static ModuleRegistration module(String name) {
        return new ModuleRegistration(
                name,
                ":module-" + name,
                "example." + name + ".Configuration",
                ":module-" + name + ":bootRun",
                ":module-" + name + ":bootJar",
                ":module-" + name + ":jar"
        );
    }
}
