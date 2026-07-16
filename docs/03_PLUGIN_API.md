# Plugin API

## Install Locally

```kotlin
pluginManagement {
    includeBuild("../..")
}
```

## Root Plugin

```kotlin
plugins {
    id("io.github.bud127.module-composer")
}

moduleComposer {
    distributionFile.set("distributions")

    commonProjectPaths.set(
        listOf(":platform-health")
    )

    commonConfigurationClasses.set(
        listOf(
            "io.github.bud127.modulecomposer.sample.health.HealthModuleConfiguration"
        )
    )
}
```

Defaults:

```text
framework              = spring-boot
generatedHostDirectory = build/module-composer/generated/combined-app
outputJar              = build/module-composer/output/combined-app.jar
distributionFile       = distributions.yml
springBootVersion      = plugin managed default
dependencyManagement   = plugin managed default
javaVersion            = 21
```

If `-PapplicationName` or a distribution YAML `applicationName` is provided and
the default generated host/output locations are used, the generated host and
bundle are written as `build/module-composer/generated/<applicationName>/` and
`build/module-composer/output/<applicationName>.jar`.

`distributionFile` may point to either a multi-preset YAML file such as
`distributions.yml` or a directory containing single distribution files such as
`distributions/enterprise.yaml`.

Application names must match `[A-Za-z0-9][A-Za-z0-9._-]*`. `-PapplicationName`
has priority over a distribution YAML `applicationName`.

If a distribution provides `artifact.fileName`, generated-host `bundleBuild`
uses that file name while the default `outputJar` file name is still configured.
If it
provides `container` metadata, generated-host `bundleBuild` writes `Dockerfile`
and `docker-compose.yml` under
`build/module-composer/output/containers/<containerServiceName>`. The container
service name is derived from `applicationName` and normalized for Docker compose
service and directory names.
If `container` metadata is absent, generated-host `bundleBuild` removes stale
generated container files from the output directory.
`container.baseImage` controls the generated Dockerfile `FROM` image and
defaults to `eclipse-temurin:21-jre`.
`container.hostPort` controls the host port published by docker compose.
`container.containerPort` controls the container port and Dockerfile `EXPOSE`.

For a single selected module, no generated host is created and `bundleBuild`
delegates to that module's standalone build task instead of copying to
`moduleComposer.outputJar`.

The root plugin discovers framework adapters through `ServiceLoader`. The
current distribution includes `SpringBootFrameworkAdapter` on the root plugin
runtime classpath through `module-composer-spring-boot`.

## Framework Adapter API

```java
public interface FrameworkAdapter {
    String frameworkId();
    void generateHost(CompositionPlan plan, GeneratedHostContext context) throws IOException;
    String standaloneRunTask(ModuleRegistration module);
    String standaloneBuildTask(ModuleRegistration module);
    String generatedRunTask();
    String generatedBuildTask();
    Path generatedArtifact();
}
```

Future frameworks should provide a new module that implements this interface
and declares a service file for `io.github.bud127.modulecomposer.core.FrameworkAdapter`.

## Build Tool Adapter API

```java
public interface BuildToolAdapter {
    String buildToolId();
    boolean projectExists(String projectReference);
    BuildInvocation standaloneRun(FrameworkAdapter frameworkAdapter, ModuleRegistration module);
    BuildInvocation standaloneBuild(FrameworkAdapter frameworkAdapter, ModuleRegistration module);
    BuildInvocation moduleArtifact(ModuleRegistration module);
    BuildInvocation projectArtifact(String projectReference);
    BuildInvocation generatedRun(FrameworkAdapter frameworkAdapter);
    BuildInvocation generatedBuild(FrameworkAdapter frameworkAdapter);
}
```

The Gradle plugin uses `GradleBuildToolAdapter`. Maven support should add a
Maven plugin/adapter module that maps these invocations to Maven reactor
projects, goals, and `target` artifacts.

See [Build Tool Adapter Architecture](06_BUILD_TOOL_ADAPTER.md) for a more
detailed Maven adaptation plan.

## Module Plugin

```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.github.bud127.module-composer-module")
}

moduleComposerModule {
    name.set("payment")
    configurationClass.set(
        "io.github.bud127.modulecomposer.sample.payment.PaymentModuleConfiguration"
    )
    standaloneRunTask.set("bootRun")
    standaloneBuildTask.set("bootJar")
}
```

The module plugin derives the project path and plain JAR task. The default
logical name is the Gradle project name with a leading `module-` removed.
Default task names are `bootRun`, `bootJar`, and `jar`.

## CLI

Public root tasks:

```text
listModules
listDistributions
explain
bundleRun
bundleBuild
bundleTest
```

Without YAML:

```bash
./gradlew listModules
./gradlew explain -Pmodules=payment,notification
./gradlew bundleRun -Pmodules=payment -Pport=9090
./gradlew bundleRun -Pmodules=payment,notification
./gradlew bundleBuild -Pmodules=payment,notification
./gradlew bundleTest -Pmodules=payment,notification
./gradlew bundleBuild -Pmodules=payment,notification -PapplicationName=custom-service
```

With YAML:

```bash
./gradlew listDistributions
./gradlew explain -Pdistribution=enterprise
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise -PexcludeModules=audit
./gradlew bundleTest -Pdistribution=enterprise
```

Generated-host execution also wires internal tasks such as
`prepareGeneratedHost`, `runGeneratedHost`, `buildGeneratedHost`,
`testGeneratedHost`, and `copyGeneratedHostJar`. They are implementation details
behind `bundleRun`, `bundleBuild`, and `bundleTest`.

`-Pmodules` and `-Pdistribution` are mutually exclusive.

Overrides:

```bash
./gradlew bundleBuild \
  -Pdistribution=enterprise \
  -PapplicationName=custom-enterprise \
  -PincludeModules=fraud \
  -PexcludeModules=audit
```

Validation:

```bash
./gradlew bundleBuild -Pdistribution=enterprise -Pvalidation=test
./gradlew bundleRun -Pmodules=payment,notification -Pvalidation=check
```

`-Pvalidation` supports `none`, `test`, and `check`. The default is `none`.
`test` and `check` run the matching task on each selected module before
standalone or generated-host execution.
