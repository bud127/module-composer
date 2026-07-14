plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation(project(":module-composer-core"))
}

gradlePlugin {
    website.set("https://github.com/bud127/module-composer")
    vcsUrl.set("https://github.com/bud127/module-composer")
    plugins {
        create("moduleComposerModule") {
            id = "io.github.bud127.module-composer-module"
            implementationClass = "io.github.bud127.modulecomposer.module.ModuleComposerModulePlugin"
            displayName = "Module Composer Module"
            description = "Registers selectable module metadata for Module Composer in a modular monorepo."
            tags.set(listOf("monorepo", "modular", "composition", "module-registry", "gradle-plugin"))
        }
    }
}
