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
    distributionFile.set("distributions.yml")

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
```

If `-PapplicationName` or a distribution YAML `applicationName` is provided and
the default generated host/output locations are used, the generated host and
bundle are written as `build/module-composer/generated/<applicationName>/` and
`build/module-composer/output/<applicationName>.jar`.

The root plugin discovers framework adapters through `ServiceLoader`. The
current distribution includes `SpringBootFrameworkAdapter`.

## Framework Adapter API

```java
public interface FrameworkAdapter {
    String frameworkId();
    void generateHost(CompositionPlan plan, GeneratedHostContext context);
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

## CLI

Without YAML:

```bash
./gradlew listModules
./gradlew bundleRun -Pmodules=payment -Pport=9090
./gradlew bundleRun -Pmodules=payment,notification
./gradlew bundleBuild -Pmodules=payment,notification
./gradlew bundleBuild -Pmodules=payment,notification -PapplicationName=custom-service
```

With YAML:

```bash
./gradlew listDistributions
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise -PexcludeModules=audit
```

Overrides:

```bash
./gradlew bundleBuild \
  -Pdistribution=enterprise \
  -PapplicationName=custom-enterprise \
  -PincludeModules=fraud \
  -PexcludeModules=audit
```
