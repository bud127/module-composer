# Architecture

## Module Structure

```text
module-composer-core/
module-composer-gradle-plugin/
module-composer-module-plugin/
module-composer-spring-boot/
future:
module-composer-maven-plugin/
module-composer-maven-adapter/
```

## Architecture Diagram

```text
Module Composer Core
        |
        +-- Module Registry
        +-- Module Selection
        +-- Distribution Loader
        +-- Composition Planner
        +-- Framework Adapter API
        +-- Build Tool Adapter API
                |
                +-- Spring Boot Adapter
                +-- Quarkus Adapter (future)
                +-- Micronaut Adapter (future)
                +-- Gradle Build Tool Adapter
                +-- Maven Build Tool Adapter (future)
```

This document is the source architecture overview for the current repository.

## Plugins

```text
io.github.bud127.module-composer
  root plugin
  parses CLI, discovers modules, builds plans, invokes adapters

io.github.bud127.module-composer-module
  module plugin
  registers selectable module metadata
```

## Class Diagram

```text
ModuleRegistry
  -> ModuleRegistration

SelectionRequest
  -> ModuleSelector
  -> ModuleSelection

CompositionPlanner
  -> CompositionPlan

BuildToolAdapter
  + buildToolId()
  + projectExists(projectReference)
  + standaloneRun(frameworkAdapter, module)
  + standaloneBuild(frameworkAdapter, module)
  + moduleArtifact(module)
  + projectArtifact(projectReference)
  + generatedRun(frameworkAdapter)
  + generatedBuild(frameworkAdapter)

GradleBuildToolAdapter
  -> Gradle task paths and archive task outputs

Future MavenBuildToolAdapter
  -> Maven reactor modules, goals, and target artifacts

CompositionPlan
  -> FrameworkAdapter
       + frameworkId()
       + generateHost(plan, context)
       + standaloneRunTask(module)
       + standaloneBuildTask(module)
       + generatedRunTask()
       + generatedBuildTask()
       + generatedArtifact()

FrameworkAdapterRegistry
  -> SpringBootFrameworkAdapter
```

## Selection

```text
-Pmodules
  -> resolve names directly from module registry
  -> do not load distribution YAML

-Pdistribution
  -> load distributions.yml or distributions/<name>.yaml
  -> resolve preset module names from module registry

-Pmodules + -Pdistribution
  -> fail as ambiguous

base selection
  -> includeModules
  -> excludeModules
```

## Decision Tree

```text
Resolved module count
        |
        +-- 1 module
        |      |
        |      +-- build-tool adapter invokes standalone run/build
        |
        +-- >1 modules
               |
               +-- build-tool adapter builds common and module artifacts
               +-- ask framework adapter to generate host
               +-- build-tool adapter runs/builds generated host
```

## Build Tool Adapter

The Gradle plugin owns Gradle execution details through `GradleBuildToolAdapter`.
Core does not call Gradle tasks directly. A Maven integration should provide a
Maven build-tool adapter that maps the same composition plan to Maven reactor
projects, goals, and generated artifacts.

See [Build Tool Adapter Architecture](06_BUILD_TOOL_ADAPTER.md) for the Maven
extension path.

## Spring Boot Adapter

`module-composer-spring-boot` owns all Spring-specific behavior:

- Spring Boot Gradle plugin generation
- dependency-management plugin generation
- `GeneratedCombinedApplication.java`
- `@SpringBootApplication`
- `@Import`
- `SpringApplication`
- `application.yml`
- `bootRun` and `bootJar` task names

## Generated Host

```text
sample/build/module-composer/generated/combined-app/
├── settings.gradle.kts
├── build.gradle.kts
├── src/main/java/com/bysa/generated/
│   └── GeneratedCombinedApplication.java
└── src/main/resources/application.yml
```

When an application name is provided, the default generated host directory is
unique per bundle, for example
`sample/build/module-composer/generated/enterprise-service/`.

The Spring Boot generated host does not scan standalone application classes
from selected modules. It imports only common and selected module configuration
classes.

## Final Build Output

Standalone mode does not copy to `build/module-composer/output`; it delegates to
the selected module's standalone build task.

Generated host mode copies the generated host artifact to:

```text
sample/build/module-composer/output/<applicationName>.jar
```

With the default application name, this becomes
`sample/build/module-composer/output/combined-app.jar`.

When a distribution provides `artifact.fileName`, generated host mode copies to
that file name instead. When a distribution provides `container` metadata,
`bundleBuild` writes `Dockerfile` and `docker-compose.yml` next to the final JAR.
Without `container` metadata, generated container files are not kept in the
output directory.
The generated Dockerfile uses `container.baseImage` or defaults to
`eclipse-temurin:21-jre`.
