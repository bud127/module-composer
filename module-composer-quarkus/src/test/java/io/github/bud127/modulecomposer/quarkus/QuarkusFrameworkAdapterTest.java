package io.github.bud127.modulecomposer.quarkus;

import io.github.bud127.modulecomposer.core.CompositionPlan;
import io.github.bud127.modulecomposer.core.DistributionDetails;
import io.github.bud127.modulecomposer.core.ExecutionMode;
import io.github.bud127.modulecomposer.core.GeneratedHostClasspath;
import io.github.bud127.modulecomposer.core.GeneratedHostContext;
import io.github.bud127.modulecomposer.core.GeneratedHostMetadata;
import io.github.bud127.modulecomposer.core.ModuleRegistration;
import io.github.bud127.modulecomposer.core.RuntimeOptions;
import io.github.bud127.modulecomposer.core.SelectionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuarkusFrameworkAdapterTest {

    @TempDir
    Path directory;

    @Test
    void generatesQuarkusHostFiles() throws Exception {
        QuarkusFrameworkAdapter adapter =
                new QuarkusFrameworkAdapter("3.37.3");
        CompositionPlan plan = new CompositionPlan(
                "quarkus",
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
                                List.of("example.payment.PaymentResource")
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
        String locator = Files.readString(
                directory.resolve(
                        "src/main/java/com/bysa/generated/GeneratedModuleResourceLocator.java"
                )
        );
        String build = Files.readString(directory.resolve("build.gradle.kts"));
        String properties = Files.readString(
                directory.resolve("src/main/resources/application.properties")
        );

        assertTrue(application.contains("@QuarkusMain"));
        assertTrue(locator.contains("@Path(\"/api/payment/{path:.*}\")"));
        assertTrue(locator.contains("new PaymentResource()"));
        assertTrue(build.contains("id(\"io.quarkus\") version \"3.37.3\""));
        assertTrue(build.contains("io.quarkus.platform:quarkus-bom:3.37.3"));
        assertTrue(build.contains("io.quarkus:quarkus-rest-jackson"));
        assertTrue(properties.contains("quarkus.application.name=custom-service"));
        assertTrue(properties.contains("quarkus.package.output-name=combined-app"));
        assertTrue(properties.contains("composer.modules=payment"));
        assertTrue(properties.contains(
                "composer.configuration-classes=example.payment.PaymentResource"
        ));
        assertEquals("quarkusDev", adapter.generatedRunTask());
        assertEquals("quarkusBuild", adapter.generatedBuildTask());
        assertEquals(Path.of("build/combined-app-runner.jar"), adapter.generatedArtifact());
    }

    private static ModuleRegistration module(String name) {
        return new ModuleRegistration(
                name,
                ":module-" + name,
                "example." + name + ".Resource",
                ":module-" + name + ":quarkusDev",
                ":module-" + name + ":quarkusBuild",
                ":module-" + name + ":jar"
        );
    }
}
