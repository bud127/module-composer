# Quarkus Demo

## Structure

```text
quarkus-demo/
├── module-payment/
├── module-notification/
├── distributions/
│   ├── payment-only.yaml
│   └── community.yaml
└── scripts/
```

## Single Module

```bash
./gradlew listModules
./gradlew explain -Pmodules=payment
./gradlew bundleRun -Pmodules=payment
./gradlew bundleTest -Pmodules=payment
```

Payment runs on port `8081`.

## Single-Module Distribution

```bash
./gradlew bundleRun -Pdistribution=payment-only
```

This also runs `module-payment` directly.

## Multiple Modules

```bash
./gradlew explain -Pmodules=payment,notification
./gradlew bundleRun -Pmodules=payment,notification
./gradlew bundleBuild -Pmodules=payment,notification
./gradlew bundleTest -Pmodules=payment,notification
```

Generated host runs on port `8080`.

## Community Distribution

```bash
./gradlew listDistributions
./gradlew explain -Pdistribution=community
./gradlew bundleBuild -Pdistribution=community
./gradlew bundleTest -Pdistribution=community
```

Output:

```text
build/module-composer/output/quarkus-community-service.jar
```

## Docker

```bash
./gradlew bundleBuild -Pdistribution=community
cd build/module-composer/output/containers/quarkus-community-service
docker compose -f docker-compose.yml up --build
```

The Docker image is built from:

```text
build/module-composer/output/quarkus-community-service.jar
```
