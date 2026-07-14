plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":module-composer-core"))
    implementation(project(":module-composer-module-plugin"))
    implementation(project(":module-composer-spring-boot"))
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    website.set("https://github.com/bud127/module-composer")
    vcsUrl.set("https://github.com/bud127/module-composer")
    plugins {
        create("moduleComposer") {
            id = "io.github.bud127.module-composer"
            implementationClass = "io.github.bud127.modulecomposer.plugin.ModuleComposerPlugin"
            displayName = "Module Composer"
            description = "Compose modules in a monorepo into runnable application bundles from CLI selections or reusable distributions."
            tags.set(listOf("monorepo", "modular", "composition", "spring-boot", "gradle-plugin"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
