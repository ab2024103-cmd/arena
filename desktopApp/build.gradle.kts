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

afterEvaluate {
    tasks.findByName("createRuntimeImage")?.let { rt ->
        tasks.named("packageReleaseAppImage") { dependsOn(rt) }
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
    doLast {
        val binaries = layout.projectDirectory.dir("build/compose/binaries").asFile
        val appDirs = binaries.walkTopDown()
            .filter { it.isDirectory && it.name == "MorseCode" && it.parentFile?.name == "app" }
            .toList()

        // Compose's createDistributable/createReleaseDistributable prepare the
        // launcher + jars but do NOT embed the jlink runtime (that happens in
        // the jpackage packaging tasks we are aliasing for). Bundle it here so
        // the portable app image is self-contained.
        val runtimeSource = appDirs.firstOrNull { it.resolve("runtime/bin/java.exe").isFile }
            ?.resolve("runtime")
            ?: binaries.walkTopDown()
                .filter { it.isDirectory && it.name == "runtime" && it.resolve("bin/java.exe").isFile }
                .firstOrNull()

        for (dir in appDirs) {
            // Debug-console launcher so users can see startup errors.
            dir.resolve("Start MorseCode (debug console).bat").writeText(
                "@echo off\r\n" +
                    "rem Runs Morse Code with a visible console so startup errors are shown.\r\n" +
                    "set \"DIR=%~dp0\"\r\n" +
                    "\"%DIR%runtime\\bin\\java.exe\" -cp \"%DIR%app\\*\" net.morsecode.desktop.MainKt %*\r\n" +
                    "echo.\r\n" +
                    "echo Morse Code exited with code %ERRORLEVEL%.\r\n" +
                    "pause\r\n",
            )
            println("packageReleaseAppImage: wrote debug launcher into ${dir.absolutePath}")

            val runtimeDir = dir.resolve("runtime")
            if (!runtimeDir.resolve("bin/java.exe").isFile) {
                val source = runtimeSource
                if (source != null) {
                    println("packageReleaseAppImage: bundling runtime from ${source.absolutePath} into ${dir.name}")
                    source.copyRecursively(runtimeDir, overwrite = true)
                } else {
                    // Fallback: jlink a runtime straight from the build JDK.
                    val jdkHome = File(System.getProperty("java.home"))
                    val jmods = jdkHome.resolve("jmods")
                    if (!jmods.isDirectory) {
                        throw GradleException("packageReleaseAppImage: no runtime image to bundle and no jmods under $jdkHome")
                    }
                    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
                    val jlink = jdkHome.resolve(if (isWindows) "bin/jlink.exe" else "bin/jlink")
                    println("packageReleaseAppImage: jlink-ing runtime from $jdkHome into ${dir.name}")
                    exec {
                        commandLine(
                            jlink.absolutePath,
                            "--add-modules",
                            "java.desktop,java.sql,java.naming,java.management,java.instrument," +
                                "java.logging,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki," +
                                "jdk.zipfs,jdk.management",
                            "--output", runtimeDir.absolutePath,
                            "--no-header-files", "--no-man-pages", "--compress=2",
                        )
                    }
                }
            }
        }
    }
}

tasks.register("validateAppImage") {
    group = "compose desktop"
    description = "Checks every packaged app image is complete and launchable."
    doLast {
        val binaries = layout.projectDirectory.dir("build/compose/binaries").asFile
        val appDirs = binaries.walkTopDown()
            .filter { it.isDirectory && it.name == "MorseCode" && it.parentFile?.name == "app" }
            .toList()
            .sortedBy { it.absolutePath }
        if (appDirs.isEmpty()) throw GradleException("validateAppImage: no app image found under $binaries")

        var anyProblems = false
        for (dir in appDirs) {
            val problems = checkAppImage(dir)
            if (problems.isNotEmpty()) {
                anyProblems = true
                problems.forEach { println("::error::validateAppImage[${dir.parentFile.parentFile.name}]: $it") }
                println("validateAppImage[${dir.parentFile.parentFile.name}] FAILED: " + problems.joinToString(" | "))
            } else {
                println("validateAppImage[${dir.parentFile.parentFile.name}]: OK (${dir.walkTopDown().count()} files)")
            }
        }
        if (anyProblems) throw GradleException("validateAppImage FAILED (see annotations above)")
    }
}

fun checkAppImage(dir: File): List<String> = buildList {
    val launcher = dir.resolve("MorseCode.exe")
    if (!launcher.isFile) add("missing launcher: MorseCode.exe")
    val cfg = dir.resolve("app/MorseCode.cfg")
    if (!cfg.isFile) {
        add("missing app/MorseCode.cfg")
    } else {
        val entries = cfg.readLines()
            .filter { it.startsWith("app.classpath=") }
            .flatMap { it.removePrefix("app.classpath=").split(";") }
            .filter { it.isNotBlank() }
        val appRoot = dir.resolve("app")
        for (e in entries) {
            // jpackage cfg entries may use the $APPDIR placeholder (expanded
            // by the launcher at runtime).
            val resolved = if (e.startsWith("$")) {
                appRoot.resolve(e.substringAfter('\\').replace('\\', '/'))
            } else {
                val f = File(e)
                if (f.isAbsolute) f else appRoot.resolve(e)
            }
            if (!resolved.exists()) add("cfg classpath entry missing: $e")
        }
        if (entries.isEmpty()) add("cfg has no app.classpath entries")
    }
    val javaExe = dir.resolve("runtime/bin/java.exe")
    if (!javaExe.isFile) add("bundled JRE missing runtime/bin/java.exe")
    val appJarCount = dir.resolve("app").listFiles()?.count { it.isFile && it.extension == "jar" } ?: 0
    if (appJarCount == 0) add("no jars in app/")
}
