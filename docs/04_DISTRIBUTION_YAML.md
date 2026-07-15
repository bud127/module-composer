# Distribution YAML

Distribution YAML is optional. It is used only for reusable module presets
selected with `-Pdistribution`.

Do not store module metadata in YAML. Project references, configuration
classes, standalone execution details, and artifact outputs belong to the
build-tool-specific module registry.

Two formats are supported:

- `distributions.yml` containing many named presets.
- `distributions/<name>.yaml` containing one distribution per file.

## Multi Distribution Format

```yaml
version: 1

distributions:
  payment-only:
    modules:
      - payment

  community:
    applicationName: community-service
    modules:
      - payment
      - notification

  enterprise:
    applicationName: enterprise-service
    modules:
      - payment
      - notification
	      - audit
```

## Single Distribution Format

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
  port: 8080
```

This can be stored at:

```text
distributions/document-platform.yaml
```

and selected with:

```bash
./gradlew bundleBuild -Pdistribution=document-platform
```

`applicationName` is optional. When present, it sets the generated Spring
application name and default generated host directory. For multi-module builds,
unless `moduleComposer.outputJar` uses a custom file name, the default bundle
output becomes `<applicationName>.jar`. A CLI value from
`-PapplicationName=...` overrides the YAML value.

For the single distribution format, `name` is used as the default
`applicationName` when `applicationName` is not provided.

`artifact.fileName` is optional. When present and `moduleComposer.outputJar`
still uses the default file name, multi-module `bundleBuild` copies the final
JAR to:

```text
build/module-composer/output/<artifact.fileName>
```

`container.image`, `container.baseImage`, and `container.port` are optional
container metadata. `image` and `port` are reported by `explain` and
`listDistributions`. For multi-module `bundleBuild`, any container metadata also
generates container files next to the final JAR:

```text
build/module-composer/output/Dockerfile
build/module-composer/output/docker-compose.yml
```

The generated compose file builds an image from the generated JAR and maps
`container.port` to the same host port.

The generated Dockerfile uses `container.baseImage` as its `FROM` image. If
`baseImage` is not provided, it defaults to `eclipse-temurin:21-jre`.

If a distribution does not define `container`, generated-host `bundleBuild` does
not create container files and removes stale generated container files from the
output directory.

Application names must match `[A-Za-z0-9][A-Za-z0-9._-]*`.
`artifact.fileName` must be a file name only, not a path.

A distribution with one module uses standalone mode. In that case `bundleBuild`
delegates to the module's standalone build task and does not copy a generated
host JAR.

## Usage

```bash
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise
./gradlew bundleBuild -Pdistribution=document-platform
```

Preset module names are resolved against modules registered by
`io.github.bud127.module-composer-module`.

`-Pdistribution` cannot be used together with `-Pmodules`.
