import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "net.morsecode.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "MorseCode"
            packageVersion = "1.0.0"
            description = "Morse Code - fast, private, fully offline LAN file transfer"
            vendor = "MorseCode"
            modules(
                "java.desktop", "java.sql", "java.naming", "java.management",
                "java.instrument", "java.logging", "java.xml",
                "jdk.unsupported", "jdk.crypto.ec", "jdk.crypto.cryptoki",
                "jdk.zipfs", "jdk.management",
            )
            windows {
                iconFile.set(project.file("src/jvmMain/resources/morse.ico"))
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = false
                upgradeUuid = "c3a4b7d2-6f1e-4a58-9d20-5e0e5ec0de01"
            }
        }
    }
}

// The CI workflow requests the historical task name "packageReleaseAppImage",
// which no longer exists in Compose Desktop 1.7. Register it as an alias for
// the release app-image task (falling back to the debug-variant task), and
// validate the produced bundle so a broken portable zip fails the build.
tasks.register("packageReleaseAppImage") {
    group = "compose desktop"
    description = "Alias for createReleaseDistributable/createDistributable (CI compatibility)."
    dependsOn(tasks.findByName("createReleaseDistributable") ?: tasks.named("createDistributable"))
    finalizedBy("validateAppImage")
}

// Verifies the app image the portable zip is built from: launcher, config,
// bundled JRE, and every classpath entry present. Fails the build otherwise.
tasks.register("validateAppImage") {
    group = "compose desktop"
    description = "Checks the packaged app image is complete and launchable."
    doLast {
        val binaries = layout.projectDirectory.dir("build/compose/binaries").asFile
        val appDirs = binaries.walkTopDown()
            .filter { it.isDirectory && it.name == "MorseCode" && it.parentFile?.name == "app" }
            .toList()
            .sortedBy { it.absolutePath }
        if (appDirs.isEmpty()) throw GradleException("validateAppImage: no app image found under $binaries")
        val dir = appDirs.first()
        val problems = mutableListOf<String>()

        val launcher = dir.resolve("MorseCode.exe")
        if (!launcher.isFile) problems.add("missing launcher: ${launcher.relativeTo(dir)}")
        val cfg = dir.resolve("app/MorseCode.cfg")
        if (!cfg.isFile) {
            problems.add("missing app/MorseCode.cfg")
        } else {
            val entries = cfg.readLines()
                .filter { it.startsWith("app.classpath=") }
                .flatMap { it.removePrefix("app.classpath=").split(";") }
                .filter { it.isNotBlank() }
            val appRoot = dir.resolve("app")
            for (e in entries) {
                val f = File(e)
                val resolved = if (f.isAbsolute) f else appRoot.resolve(e)
                if (!resolved.exists()) problems.add("cfg classpath entry missing: $e")
            }
            if (entries.isEmpty()) problems.add("cfg has no app.classpath entries")
        }
        val javaExe = dir.resolve("runtime/bin/java.exe")
        if (!javaExe.isFile) problems.add("bundled JRE missing runtime/bin/java.exe")
        val appJarCount = dir.resolve("app").listFiles()?.count { it.isFile && it.extension == "jar" } ?: 0
        if (appJarCount == 0) problems.add("no jars in app/")

        println("validateAppImage: checking ${dir.absolutePath} (found ${appDirs.size} app image(s): ${appDirs.map { it.parentFile.parentFile.name }})")
        println("validateAppImage: jars=$appJarCount totalFiles=${dir.walkTopDown().count()}")
        if (problems.isNotEmpty()) {
            throw GradleException("validateAppImage FAILED:\n  - " + problems.joinToString("\n  - "))
        }
        println("validateAppImage: OK")
    }
}
