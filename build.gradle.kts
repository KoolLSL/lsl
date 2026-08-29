plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.3"
    antlr
}

// Use a build folder out of OneDrive or GitHub
layout.buildDirectory.set(File("C:/IntelliJBuild/${project.name}"))

group = "kool (from io.github.riej)"
version = "0.2.4"

sourceSets["main"].java.srcDirs("src/main/gen")

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.11.1")
    implementation("org.antlr:antlr4-runtime:4.11.1")

    // JUnit 4 dependency for IntelliJ test framework support
    testImplementation("junit:junit:4.13.2")
    // OpenTest4J required by IntelliJ 2024.3 BasePlatformTestCase
    testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"
    arguments = arguments + listOf("-visitor", "-package", "io.github.koollsl.lsl.parser")
    outputDirectory = file("src/main/gen")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.compileTestKotlin {
    dependsOn(tasks.generateTestGrammarSource)
}

// Configure Gradle IntelliJ Plugin
intellij {
    version.set("2024.3")
    type.set("IC")
    plugins.set(listOf("java"))
}

// Disable buildSearchableOptions for fast local development by default.
val isRelease = properties["release"]?.toString()?.toBoolean() == true

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("243")
        untilBuild.set("")
    }

    // Skips background settings indexing to keep build under 1 second
    buildSearchableOptions {
        enabled = false
    }

    runIde {
        autoReloadPlugins.set(true)
        args = listOf("C:\\Users\\Me\\OneDrive\\Documents\\SL\\IntelliJ\\Rezzer\\Rezzer DEV")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}