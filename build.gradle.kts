plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform")
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(25)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        description = providers.gradleProperty("pluginDescription")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = provider { null }
        }
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf(
            providers.gradleProperty("pluginVersion")
                .get()
                .substringAfter("-", "default")
                .substringBefore('.')
                .ifEmpty { "default" }
        )
    }
}

val fetchModelerAssets by tasks.registering(Exec::class) {
    workingDir = rootDir
    commandLine("bash", "scripts/fetch-modeler-assets.sh")
    outputs.file("src/main/resources/bpmn-editor/camunda-cloud-modeler.production.min.js")
    onlyIf { !file("src/main/resources/bpmn-editor/camunda-cloud-modeler.production.min.js").exists() }
}

tasks.matching { it.name.contains("prepareSandbox") || it.name == "buildPlugin" }.configureEach {
    dependsOn(fetchModelerAssets)
}
