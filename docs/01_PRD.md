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

Distribution YAML is required only when `-Pdistribution` is used. The plugin
supports `distributions.yml` with many presets and
`distributions/<name>.yaml` with one distribution per file:

```bash
./gradlew bundleBuild -Pdistribution=enterprise
```

Large or frequently reused combinations should use `-Pdistribution`.

Single distribution files may define artifact and container metadata:

```yaml
name: document-platform
version: 0.1.0
modules:
  - document
  - email
  - upload
artifact:
  fileName: application.jar
container:
  image: ghcr.io/bud127/document-platform
  baseImage: eclipse-temurin:21-jre
  hostPort: 8080
  containerPort: 8080
```

`-Pmodules` and `-Pdistribution` are mutually exclusive. A command that provides
both must fail with a clear ambiguity error.

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

### Validation

`bundleRun` and `bundleBuild` accept optional selected-module validation:

```bash
./gradlew bundleBuild -Pdistribution=enterprise -Pvalidation=test
./gradlew bundleBuild -Pdistribution=enterprise -Pvalidation=check
```

Supported values:

```text
none
test
check
```

Default is `none`. `test` runs `:module-x:test` for each selected module before
run/build execution. `check` runs `:module-x:check`.

## Output

Single-module selections bypass the generated host. `bundleBuild` delegates to
the selected module's standalone build task, so the build output is owned by that
module.

Multi-module selections use a generated host and copy the final executable JAR
to:

```text
build/module-composer/output/<applicationName>.jar
```

The default `applicationName` is `combined-app`, so the default generated-host
output is:

```text
build/module-composer/output/combined-app.jar
```

`-PapplicationName` has priority over distribution YAML `applicationName`.
Application names must match `[A-Za-z0-9][A-Za-z0-9._-]*`.
If `artifact.fileName` is provided and the default output location is used,
multi-module `bundleBuild` writes that file name instead of
`<applicationName>.jar`.
If `container` metadata is provided, multi-module `bundleBuild` writes a
Dockerfile and `docker-compose.yml` under
`build/module-composer/output/containers/<applicationName>`.
The generated Dockerfile must use `container.baseImage` when provided and
default to `eclipse-temurin:21-jre` otherwise.
The generated Dockerfile must expose `container.containerPort`, and generated
docker compose must publish `container.hostPort:container.containerPort`.

## MVP Commands

- `bundleRun`
- `bundleBuild`
- `explain`
- `listModules`
- `listDistributions`

## Acceptance Criteria

- Distribution YAML is optional for `-Pmodules`.
- Distribution YAML is required for `-Pdistribution`.
- `distributions.yml` is supported for multi-preset files.
- `distributions/<name>.yaml` is also supported for single distribution files.
- `-Pmodules` and `-Pdistribution` cannot be used together.
- `-Pvalidation` supports `none`, `test`, and `check`.
- No permanent `applications/combined-app` project.
- One module bypasses generated host.
- Multiple modules create generated host through the framework adapter.
- Distribution with one module runs directly.
- Distribution with multiple modules uses generated host.
- Single-module `bundleBuild` uses the module's standalone build output.
- Multi-module `bundleBuild` copies the generated host JAR to
  `build/module-composer/output/<applicationName>.jar`.
- Multi-module `bundleBuild` uses `artifact.fileName` when a distribution
  provides it.
- Multi-module `bundleBuild` writes container files when a distribution provides
  `container` metadata, and does not leave stale container files when metadata is
  absent.
- Docker Compose consumes the generated JAR.
