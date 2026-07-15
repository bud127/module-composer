plugins {
    id("io.github.bud127.module-composer")
}

allprojects {
    group = "io.github.bud127.modulecomposer.sample"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

moduleComposer {
    distributionFile.set("distributions")

    outputJar.set(
        layout.buildDirectory.file(
            "module-composer/output/combined-app.jar"
        )
    )

    springBootVersion.set("3.5.7")
    dependencyManagementVersion.set("1.1.7")
    javaVersion.set(21)
}
