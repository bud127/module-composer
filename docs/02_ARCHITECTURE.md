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
  -> do not load distributions.yml

-Pdistribution
  -> load distributions.yml
  -> resolve preset module names from module registry

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
sample/build/module-composer/combined-app/
├── settings.gradle.kts
├── build.gradle.kts
├── src/main/java/com/bysa/generated/
│   └── GeneratedCombinedApplication.java
└── src/main/resources/application.yml
```

The Spring Boot generated host does not scan standalone application classes
from selected modules. It imports only common and selected module configuration
classes.

## Final Build Output

```text
sample/build/module-composer/output/combined-app.jar
```
