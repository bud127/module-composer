# Spring Boot Demo

## Structure

```text
springboot-demo/
├── module-payment/
├── module-notification/
├── module-audit/
├── distributions/
│   ├── payment-only.yaml
│   ├── community.yaml
│   └── enterprise.yaml
└── scripts/
```

## Single module

```bash
./gradlew explain -Pmodules=payment
./gradlew bundleRun -Pmodules=payment
```

Payment runs on port `8081`.

## Single-module distribution

```bash
./gradlew bundleRun -Pdistribution=payment-only
```

This also runs `module-payment` directly.

## Multiple modules

```bash
./gradlew explain -Pmodules=payment,notification
./gradlew bundleRun -Pmodules=payment,notification
```

Generated host runs on port `8080`.

## Enterprise distribution

```bash
./gradlew bundleBuild -Pdistribution=enterprise
```

Output:

```text
build/module-composer/output/application.jar
```

## Docker

```bash
./gradlew bundleBuild -Pdistribution=enterprise
cd build/module-composer/output
docker compose -f docker-compose.yml up --build
```

The Docker image is built from:

```text
build/module-composer/output/application.jar
```

`Dockerfile` and `docker-compose.yml` are generated in
`build/module-composer/output` because `distributions/enterprise.yaml` defines
container metadata.
