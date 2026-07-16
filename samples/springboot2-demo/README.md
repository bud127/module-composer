# Spring Boot 2 Demo

This sample uses Spring Boot `2.7.18`, Spring dependency-management
`1.0.15.RELEASE`, Java `17`, and Gradle `7.6.4`.

## Structure

```text
springboot2-demo/
├── module-payment/
├── module-notification/
├── module-audit/
├── distributions/
│   ├── payment-only.yaml
│   ├── community.yaml
│   └── enterprise.yaml
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

## Multiple Modules

```bash
./gradlew explain -Pmodules=payment,notification
./gradlew bundleRun -Pmodules=payment,notification
./gradlew bundleTest -Pmodules=payment,notification
```

Generated host runs on port `8080`.

## Enterprise Distribution

```bash
./gradlew listDistributions
./gradlew explain -Pdistribution=enterprise
./gradlew bundleBuild -Pdistribution=enterprise
./gradlew bundleTest -Pdistribution=enterprise
```

Output:

```text
build/module-composer/output/application.jar
```

## Notes

The root `moduleComposer` block pins the generated host to Spring Boot 2:

```kotlin
moduleComposer {
    springBootVersion.set("2.7.18")
    dependencyManagementVersion.set("1.0.15.RELEASE")
    javaVersion.set(17)
}
```

The sample `gradlew` bootstrap defaults to Gradle `7.6.4`, which is compatible
with Spring Boot Gradle Plugin `2.7.x`.
