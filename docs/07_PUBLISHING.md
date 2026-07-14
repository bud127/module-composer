# Publishing

Module Composer can be published in two ways:

- Internal Gradle usage through a Maven repository such as Nexus or Artifactory.
- Public Gradle Plugin Portal usage for the Gradle plugin IDs.

## Coordinates

Current Maven group and plugin version:

```text
group   = io.github.bud127
version = 0.1.0
```

Plugin IDs:

```text
io.github.bud127.module-composer
io.github.bud127.module-composer-module
```

The legacy `com.bysa.*` plugin aliases are intentionally not published. Gradle
Plugin Portal requires the plugin ID and Maven group to share the same top-level
namespace.

## Validate Locally

```bash
./gradlew test
./gradlew publishToMavenLocal
```

Use from another Gradle build:

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
plugins {
    id("io.github.bud127.module-composer") version "0.1.0"
}
```

## Publish To Internal Maven

Publish all runtime libraries, plugin artifacts, and marker publications:

```bash
./gradlew publishAllPublicationsToModuleComposerRepository \
  -PmoduleComposerPublishUrl=https://repo.example.com/releases \
  -PmoduleComposerPublishUsername="$MAVEN_USERNAME" \
  -PmoduleComposerPublishPassword="$MAVEN_PASSWORD"
```

Environment variables are also supported:

```bash
MODULE_COMPOSER_PUBLISH_USERNAME=... \
MODULE_COMPOSER_PUBLISH_PASSWORD=... \
./gradlew publishAllPublicationsToModuleComposerRepository \
  -PmoduleComposerPublishUrl=https://repo.example.com/releases
```

If `moduleComposerPublishUrl` is omitted, artifacts are published to:

```text
build/repo
```

Consumers should add that Maven repository to `pluginManagement.repositories`.

## Publish To Gradle Plugin Portal

Before publishing publicly:

1. Ensure the GitHub repository and documentation URLs are public and correct.
2. Publish non-plugin runtime artifacts somewhere public, such as Maven Central:
   - `module-composer-core`
   - `module-composer-spring-boot`
3. Create a Gradle Plugin Portal account and API key.
4. Configure credentials in `~/.gradle/gradle.properties` or use environment
   variables.

Credentials through Gradle properties:

```properties
gradle.publish.key=...
gradle.publish.secret=...
```

Credentials through environment variables:

```bash
export GRADLE_PUBLISH_KEY=...
export GRADLE_PUBLISH_SECRET=...
```

Publish the two plugin IDs:

```bash
./gradlew :module-composer-module-plugin:publishPlugins
./gradlew :module-composer-gradle-plugin:publishPlugins
```

The first public release goes through Gradle Plugin Portal review.

## Important Constraint

`module-composer-gradle-plugin` depends on runtime artifacts from this repository.
For public Gradle Plugin Portal usage, those dependencies must be publicly
resolvable by plugin consumers. If they are private, use an internal Maven
repository instead of the public Plugin Portal.
