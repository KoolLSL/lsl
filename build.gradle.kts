import java.util.zip.ZipFile

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.3"
    antlr
}

// Use a build folder out of OneDrive or GitHub
layout.buildDirectory.set(File("C:/IntelliJBuild/${project.name}"))

group = "kool"
version = "0.2.4"

sourceSets["main"].java.srcDirs("src/main/gen")

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4:4.11.1")
    implementation("org.antlr:antlr4-runtime:4.11.1")

    // JUnit 4 dependency for IntelliJ test framework support
    //testImplementation("junit:junit:4.13.2")
    // OpenTest4J required by IntelliJ 2024.3 BasePlatformTestCase
    //testRuntimeOnly("org.opentest4j:opentest4j:1.3.0")
}

// Disable test compiling
sourceSets {
    test {
        java.setSrcDirs(emptyList<String>())
        resources.setSrcDirs(emptyList<String>())
        kotlin.setSrcDirs(emptyList<String>())
    }
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

// Install plugin + restart IntelliJ
// Detect newest IntelliJ installation under %APPDATA%\JetBrains
fun detectIntelliJPluginsDir(): File {
    val jetbrainsDir = File(System.getenv("APPDATA"), "JetBrains")
    val ideaDirs = jetbrainsDir.listFiles { f ->
        f.isDirectory && f.name.startsWith("IntelliJIdea")
    } ?: emptyArray()

    val newest = ideaDirs.sortedByDescending { it.name }.firstOrNull()
        ?: error("No IntelliJ installations found in APPDATA/JetBrains")

    println("✔ Detected IntelliJ: ${newest.name}")
    return File(newest, "plugins")
}

tasks.register("installPluginToIDE") {
    dependsOn("buildPlugin")

    doLast {
        val zip = layout.buildDirectory.file(
            "distributions/${project.name}-${project.version}.zip"
        ).get().asFile

        val idePluginsDir = detectIntelliJPluginsDir()

        val pluginId = "lsl"
        val targetDir = File(idePluginsDir, pluginId)

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
            println("✔ Removed old plugin folder: $targetDir")
        }

        println("✔ Unpacking plugin ZIP into: $targetDir")

        ZipFile(zip).use { zipFile ->
            val topLevel = zipFile.entries().asIterator()
                .asSequence()
                .map { it.name.substringBefore("/") }
                .first()

            zipFile.entries().asIterator().forEach { entry ->
                val relative = entry.name.removePrefix("$topLevel/")
                if (relative.isEmpty()) return@forEach

                val outFile = File(targetDir, relative)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile.mkdirs()
                    zipFile.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        println("✔ Plugin installed correctly into: $targetDir")

        project.exec {
            commandLine(
                "powershell",
                "-NoProfile",
                "-Command",
                // Stop IntelliJ if running
                "\$p = Get-Process idea64 -ErrorAction SilentlyContinue; " +
                        "if (\$p) { \$p.CloseMainWindow(); Start-Sleep -Seconds 2; \$p.Kill() }; " +
                        // Wait until IntelliJ is fully terminated
                        "while (Get-Process idea64 -ErrorAction SilentlyContinue) { Start-Sleep -Seconds 1 }; " +
                        // Delay before restart
                        "Start-Sleep -Seconds 2; " +
                        // Restart IntelliJ
                        "Start-Process \"C:\\\\Program Files\\\\JetBrains\\\\IntelliJ IDEA 2026.2.1\\\\bin\\\\idea64.exe\""
            )
        }
    }
}

