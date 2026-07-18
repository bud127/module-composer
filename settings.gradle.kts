pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "module-composer"

include(":module-composer-core")
include(":module-composer-gradle-plugin")
include(":module-composer-module-plugin")
include(":module-composer-spring-boot")
include(":module-composer-quarkus")
