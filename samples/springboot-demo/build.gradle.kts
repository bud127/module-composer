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

    javaVersion.set(21)
}
