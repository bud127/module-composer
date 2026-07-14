# Spring Boot Demo

## Structure

```text
springboot-demo/
├── module-payment/
├── module-notification/
├── module-audit/
├── distributions.yml
├── Dockerfile
└── compose.yml
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
build/module-composer/output/combined-app.jar
```

## Docker

```bash
./gradlew bundleBuild -Pdistribution=enterprise
docker compose up --build
```
