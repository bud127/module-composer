# Product Requirements Document

## Product

Module Composer

## Goal

Allow developers to select one or more modules using direct CLI module names or
optional YAML distribution presets, while keeping the core framework
independent and build-tool adaptable.

## Required Behavior

### Module Registry

Selectable modules register metadata through Gradle DSL by applying:

```kotlin
id("io.github.bud127.module-composer-module")
```

The root plugin discovers registered modules across the build.

### Framework Adapter

Spring Boot is the default adapter. Core selection and planning code must not
depend on Spring Boot, Quarkus, Micronaut, or Helidon.

### Build Tool Adapter

Gradle is the first implemented build-tool adapter. Core selection and planning
code must not depend on Gradle APIs. Maven support should be added by a Maven
plugin/build-tool adapter that maps composition plans to Maven reactor modules,
goals, and artifacts.

### Direct CLI Modules

`distributions.yml` is not required:

```bash
./gradlew bundleRun -Pmodules=payment
./gradlew bundleRun -Pmodules=payment,notification
```

Small or ad hoc selections should use `-Pmodules`.

### Distribution Presets

`distributions.yml` is required only when `-Pdistribution` is used:

```bash
./gradlew bundleBuild -Pdistribution=enterprise
```

Large or frequently reused combinations should use `-Pdistribution`.

### Overrides

Selection priority:

```text
base selection
-> includeModules
-> excludeModules
```

`excludeModules` has the highest priority.

### Runtime Port

`bundleRun` accepts:

```bash
./gradlew bundleRun -Pmodules=payment -Pport=9090
```

The port must be an integer between `1` and `65535`.

## Output

```text
build/module-composer/output/combined-app.jar
```

`-PapplicationName` or distribution YAML `applicationName` can override the
default generated bundle file name to `<applicationName>.jar`.

## MVP Commands

- `bundleRun`
- `bundleBuild`
- `explain`
- `listModules`
- `listDistributions`

## Acceptance Criteria

- `distributions.yml` is optional for `-Pmodules`.
- `distributions.yml` is required for `-Pdistribution`.
- No permanent `applications/combined-app` project.
- One module bypasses generated host.
- Multiple modules create generated host through the framework adapter.
- Distribution with one module runs directly.
- Distribution with multiple modules uses generated host.
- Docker Compose consumes the generated JAR.
