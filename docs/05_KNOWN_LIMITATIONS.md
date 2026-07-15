# Known Limitations

- Spring Boot is the only implemented framework adapter today.
- Gradle is the only implemented build-tool adapter today.
- Maven support is prepared architecturally but not implemented yet.
- `module-composer-maven-plugin` and `module-composer-maven-adapter` do not
  exist yet.
- Quarkus, Micronaut, and Helidon adapters are not implemented yet.
- The generated host uses a nested Gradle build, so the first run may download
  framework plugins independently.
- Module dependency conflict resolution is left to the active build tool and
  framework.
- `-Pport` affects `bundleRun` only. Built JARs should receive runtime ports with
  a runtime argument such as `java -jar app.jar --server.port=9090`.
- Module configuration classes must be provided explicitly in module DSL.
- The root plugin currently ships with the Spring Boot adapter on its runtime
  classpath.
