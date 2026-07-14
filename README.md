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

`distributions.yml` is optional. Use `-Pmodules` for small or ad hoc module
sets. Use `-Pdistribution` for large or frequently reused combinations.

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
docs/06_BUILD_TOOL_ADAPTER.md
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

Without YAML:

```bash
./gradlew listModules
./gradlew bundleRun -Pmodules=payment -Pport=9090
./gradlew bundleRun -Pmodules=payment,notification
./gradlew bundleBuild -Pmodules=payment,notification
```

With YAML presets:

```bash
./gradlew listDistributions
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise -PexcludeModules=audit
```

Docker:

```bash
./gradlew bundleBuild -Pdistribution=enterprise
docker compose up --build
```

Combined output:

```text
samples/springboot-demo/build/module-composer/output/combined-app.jar
```
