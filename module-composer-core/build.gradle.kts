plugins {
    `java-library`
    `maven-publish`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    api(libs.snakeyaml)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val springBootVersion = libs.versions.springBoot.get()
    val dependencyManagementVersion = libs.versions.dependencyManagement.get()

    inputs.property("springBootVersion", springBootVersion)
    inputs.property("dependencyManagementVersion", dependencyManagementVersion)

    filesMatching("module-composer-defaults.properties") {
        expand(
            mapOf(
                "springBootVersion" to springBootVersion,
                "dependencyManagementVersion" to dependencyManagementVersion
            )
        )
    }
}

tasks.test {
    useJUnitPlatform()
}
