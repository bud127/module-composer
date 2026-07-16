# Module Composer

Module Composer is a framework-agnostic and build-tool-adaptable composition
engine for modular applications. The current executable integration is Gradle,
and Spring Boot is implemented as the first framework adapter.

## Core Behavior

```text
1 selected module
-> run/build the module directly

More than 1 selected module
-> generate a temporary host through the selected framework adapter
-> generate exactly one framework application entry point
-> run/build a combined application
```

Distribution YAML is optional. Use `-Pmodules` for small or ad hoc module sets.
Use `-Pdistribution` for large or frequently reused combinations. Distribution
presets can live in `distributions.yml` or one file per distribution under
`distributions/<name>.yaml`.

## Module Structure

```text
module-composer-core
  framework/build-tool independent registry, selection, distribution, planner,
  FrameworkAdapter API, and BuildToolAdapter API

module-composer-gradle-plugin
  Gradle integration, CLI parsing, Gradle task wiring, plan execution

module-composer-module-plugin
  module Gradle plugin, selectable module metadata registration

module-composer-spring-boot
  Spring Boot FrameworkAdapter implementation and generated host factory
```

Docs:

```text
docs/02_ARCHITECTURE.md
docs/03_PLUGIN_API.md
docs/08_USAGE.md
docs/09_BUILD_RUN_FLOW.md
docs/10_PRESENTATION_OUTLINE.md
docs/11_SOURCE_CODE_WALKTHROUGH.md
docs/12_METHOD_REFERENCE.md
docs/06_BUILD_TOOL_ADAPTER.md
docs/07_PUBLISHING.md
```

## Framework Adapter Architecture

```text
Module Composer Core
        |
        +-- Module Registry
        +-- Module Selection
        +-- Distribution Loader
        +-- Composition Planner
        +-- FrameworkAdapter API
        +-- BuildToolAdapter API
                |
                +-- SpringBootFrameworkAdapter
                +-- Quarkus adapter (future)
                +-- GradleBuildToolAdapter
                +-- Maven adapter (future)
```

Future frameworks should be added as new adapter modules that implement
`FrameworkAdapter` and register through `ServiceLoader`. Future build tools
should add their own integration module that implements `BuildToolAdapter`
instead of changing core selection or planning code.

Maven support is not implemented yet. The intended path is a Maven plugin plus
Maven build-tool adapter that reuse `module-composer-core`.

## Plugins

Root project:

```kotlin
plugins {
    id("io.github.bud127.module-composer")
}
```

Selectable module project:

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
}
```

## Try The Sample

```bash
cd samples/springboot-demo
chmod +x gradlew
```

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

With YAML presets:

```bash
./gradlew listDistributions
./gradlew explain -Pdistribution=enterprise
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise -PexcludeModules=audit
./gradlew bundleTest -Pdistribution=enterprise
```

Docker:

```bash
./gradlew bundleBuild -Pdistribution=enterprise
cd build/module-composer/output/containers/enterprise-service
docker compose -f docker-compose.yml up --build
```

Combined output without an application name:

```text
samples/springboot-demo/build/module-composer/output/combined-app.jar
```

This output path is produced for generated-host builds. Single-module builds
delegate to that module's standalone build task. When `-PapplicationName` or a
distribution `applicationName` is provided, the default generated-host output
name becomes `<applicationName>.jar`.

The sample `enterprise` distribution uses:

```text
samples/springboot-demo/build/module-composer/output/application.jar
```

The sample sets `artifact.fileName: application.jar` and container metadata,
including `container.baseImage`, so `bundleBuild` also writes a Dockerfile and
`docker-compose.yml` under
`build/module-composer/output/containers/<containerServiceName>`.
The container service name is derived from `applicationName` and normalized for
Docker compose service and directory names.
`container.hostPort` is the host port in docker compose, and
`container.containerPort` is the container port used by compose and Dockerfile
`EXPOSE`.

## Publishing

Validate local publishing:

```bash
./gradlew test
./gradlew publishToMavenLocal
```

Publish all artifacts to an internal Maven repository:

```bash
./gradlew publishAllPublicationsToModuleComposerRepository \
  -PmoduleComposerPublishUrl=https://repo.example.com/releases
```

Publish plugin IDs to the Gradle Plugin Portal:

```bash
./gradlew :module-composer-module-plugin:publishPlugins
./gradlew :module-composer-gradle-plugin:publishPlugins
```

See [Publishing](docs/07_PUBLISHING.md) for credentials and public dependency
requirements.
