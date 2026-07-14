plugins {
    base
}

group = "io.github.bud127"
version = "0.1.0"

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension>("publishing") {
            if (plugins.hasPlugin("java-library") && !plugins.hasPlugin("java-gradle-plugin")) {
                publications {
                    if (findByName("mavenJava") == null) {
                        create<MavenPublication>("mavenJava") {
                            from(components["java"])
                        }
                    }
                }
            }
            repositories {
                maven {
                    name = "moduleComposer"
                    url = uri(
                        providers.gradleProperty("moduleComposerPublishUrl")
                            .orElse(rootProject.layout.buildDirectory.dir("repo").map { it.asFile.absolutePath })
                            .get()
                    )
                    val publishUsername = providers.gradleProperty("moduleComposerPublishUsername")
                        .orElse(System.getenv("MODULE_COMPOSER_PUBLISH_USERNAME") ?: "")
                        .get()
                    val publishPassword = providers.gradleProperty("moduleComposerPublishPassword")
                        .orElse(System.getenv("MODULE_COMPOSER_PUBLISH_PASSWORD") ?: "")
                        .get()
                    if (publishUsername.isNotBlank() || publishPassword.isNotBlank()) {
                        credentials(PasswordCredentials::class) {
                            username = publishUsername
                            password = publishPassword
                        }
                    }
                }
            }
        }
    }
}
