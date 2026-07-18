pluginManagement {
    includeBuild("../..")

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    val versionCatalog = file("../../gradle/libs.versions.toml").readText()
    fun catalogVersion(alias: String): String {
        val prefix = "$alias = \""
        return versionCatalog
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix) && it.endsWith("\"") }
            ?.removePrefix(prefix)
            ?.removeSuffix("\"")
            ?: error("Missing version '$alias' in root version catalog")
    }

    plugins {
        id("io.quarkus") version catalogVersion("quarkus")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "quarkus-demo"

include(":module-payment")
include(":module-notification")
