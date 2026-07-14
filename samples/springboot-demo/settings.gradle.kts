pluginManagement {
    includeBuild("../..")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    plugins {
        id("org.springframework.boot") version "3.5.7"
        id("io.spring.dependency-management") version "1.1.7"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "springboot-demo"

include(":platform-health")
include(":module-payment")
include(":module-notification")
include(":module-audit")
