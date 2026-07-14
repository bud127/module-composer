# Build Tool Adapter Architecture

Module Composer has two independent adapter axes:

```text
Framework adapter
  decides how a composed application is generated and run
  examples: Spring Boot, Quarkus, Micronaut

Build-tool adapter
  decides how modules, artifacts, and generated hosts are executed
  examples: Gradle, Maven
```

The current implementation ships only the Gradle build-tool adapter. Maven is
not implemented yet, but the core API is prepared so Maven support can be added
without changing module selection, distribution resolution, or framework
generation.

## Core Contract

```java
public interface BuildToolAdapter {
    String buildToolId();

    boolean projectExists(String projectReference);

    BuildInvocation standaloneRun(
            FrameworkAdapter frameworkAdapter,
            ModuleRegistration module
    );

    BuildInvocation standaloneBuild(
            FrameworkAdapter frameworkAdapter,
            ModuleRegistration module
    );

    BuildInvocation moduleArtifact(ModuleRegistration module);

    BuildInvocation projectArtifact(String projectReference);

    BuildInvocation generatedRun(FrameworkAdapter frameworkAdapter);

    BuildInvocation generatedBuild(FrameworkAdapter frameworkAdapter);
}
```

`BuildInvocation` is intentionally generic:

```java
public record BuildInvocation(
        String displayName,
        List<String> steps
) {
}
```

For Gradle, `steps` are task names or task paths.

For Maven, `steps` can become goals, phases, or an internal execution model
owned by a Maven plugin.

## Current Gradle Mapping

```text
standaloneRun
  -> :module-payment:bootRun

standaloneBuild
  -> :module-payment:bootJar

moduleArtifact
  -> :module-payment:jar

projectArtifact
  -> :platform-health:jar

generatedRun
  -> bootRun in generated host

generatedBuild
  -> clean, bootJar in generated host
```

The root Gradle plugin still owns Gradle task registration, `GradleBuild`, and
archive file providers. These details do not leak into core selection or
planning.

## Future Maven Mapping

A Maven implementation should add modules similar to:

```text
module-composer-maven-plugin/
module-composer-maven-adapter/
```

Expected responsibilities:

- Read module metadata from Maven plugin configuration.
- Register modules into `ModuleRegistry`.
- Resolve reactor projects by Maven coordinates or module paths.
- Map standalone run/build to Maven goals.
- Resolve module artifacts from `target`.
- Generate the host through the selected `FrameworkAdapter`.
- Run/build the generated host through Maven goals.
- Copy the final generated artifact to the configured output location.

## What Maven Should Not Reimplement

Maven support should reuse these core pieces:

- `ModuleRegistry`
- `ModuleSelector`
- `DistributionLoader`
- `CompositionPlanner`
- `CompositionPlan`
- `FrameworkAdapter`
- `RuntimeOptions`

Adding Maven should not require changing `module-composer-core` unless the
generic adapter contract itself is missing a build-tool-independent concept.
