# Distribution YAML

`distributions.yml` is optional. It is used only for reusable module presets
selected with `-Pdistribution`.

Do not store module metadata in YAML. Project references, configuration
classes, standalone execution details, and artifact outputs belong to the
build-tool-specific module registry.

## Format

```yaml
version: 1

distributions:
  payment-only:
    modules:
      - payment

  community:
    modules:
      - payment
      - notification

  enterprise:
    modules:
      - payment
      - notification
      - audit
```

## Usage

```bash
./gradlew bundleRun -Pdistribution=community
./gradlew bundleBuild -Pdistribution=enterprise
```

Preset module names are resolved against modules registered by
`io.github.bud127.module-composer-module`.
