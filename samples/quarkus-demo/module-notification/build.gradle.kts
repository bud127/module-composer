plugins {
    java
    id("io.quarkus")
    id("io.github.bud127.module-composer-module")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

moduleComposerModule {
    name.set("notification")
    configurationClass.set(
        "io.github.bud127.modulecomposer.sample.quarkus.notification.NotificationResource"
    )
    standaloneRunTask.set("quarkusDev")
    standaloneBuildTask.set("quarkusBuild")
}

dependencies {
    implementation(enforcedPlatform("io.quarkus.platform:quarkus-bom:${libs.versions.quarkus.get()}"))
    implementation("io.quarkus:quarkus-rest-jackson")
    implementation("io.quarkus:quarkus-smallrye-health")
    testImplementation("io.quarkus:quarkus-junit5")
    testImplementation("io.rest-assured:rest-assured")
}

tasks.named<Jar>("jar") {
    enabled = true
    exclude("application.properties")
}

tasks.test {
    useJUnitPlatform()
}
