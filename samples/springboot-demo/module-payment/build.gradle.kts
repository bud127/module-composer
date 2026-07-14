plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("io.github.bud127.module-composer-module")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

moduleComposerModule {
    name.set("payment")
    configurationClass.set(
        "io.github.bud127.modulecomposer.sample.payment.PaymentModuleConfiguration"
    )
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<Jar>("jar") {
    enabled = true
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("payment-app.jar")
}

tasks.test {
    useJUnitPlatform()
}
