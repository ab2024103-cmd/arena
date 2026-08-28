import java.io.ByteArrayOutputStream
import java.util.zip.ZipFile
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

        buildTypes.release.proguard {
            // Keep the default release pipeline (it packages the main jar),
            // but force-keep all project classes: the default rules stripped
            // the entrypoint from the packaged jars, so the launcher died with
            // ClassNotFoundException: net.morsecode.desktop.MainKt.
            configurationFiles.from(project.file("proguard-desktop.pro"))
        }

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
    description = "Builds a self-contained portable app image (bat launchers + plain jars + bundled jlink runtime)."
    dependsOn(tasks.named("jar"))
    finalizedBy("validateAppImage")

    doLast {
        val binaries = layout.projectDirectory.dir("build/compose/binaries").asFile
        val imageDir = binaries.resolve("main-release/app/MorseCode")
        val appDir = imageDir.resolve("app")
        appDir.deleteRecursively()
        appDir.mkdirs()

        // Main application jar (plain, unobfuscated, contains the entrypoint).
        val mainJar = tasks.named("jar", Jar::class.java).get().archiveFile.get().asFile
        mainJar.copyTo(appDir.resolve("MorseCode.jar"), overwrite = true)

        // All runtime dependency jars.
        val deps = sourceSets.main.get().runtimeClasspath.filter { it.isFile && it.extension == "jar" }
        deps.forEach { it.copyTo(appDir.resolve(it.name), overwrite = true) }
        println("packageReleaseAppImage: app/ jars: ${appDir.listFiles()!!.size} (main: ${mainJar.name})")

        // Bundled runtime: reuse compose's jlink image when present, else jlink
        // from the build JDK ourselves.
        val runtimeDir = imageDir.resolve("runtime")
        runtimeDir.deleteRecursively()
        val rt = binaries.walkTopDown()
            .filter { it.isDirectory && it.name == "runtime" && it.resolve("bin/java.exe").isFile && it != runtimeDir }
            .firstOrNull()
        if (rt != null) {
            println("packageReleaseAppImage: bundling runtime from ${rt.absolutePath}")
            rt.copyRecursively(runtimeDir, overwrite = true)
        } else {
            val jdkHome = File(System.getProperty("java.home"))
            val jmods = jdkHome.resolve("jmods")
            if (!jmods.isDirectory) {
                throw GradleException("packageReleaseAppImage: no runtime image to bundle and no jmods under $jdkHome")
            }
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            val jlink = jdkHome.resolve(if (isWindows) "bin/jlink.exe" else "bin/jlink")
            println("packageReleaseAppImage: jlink-ing runtime from $jdkHome")
            val err = ByteArrayOutputStream()
            val result = exec {
                commandLine(
                    jlink.absolutePath,
                    "--add-modules",
                    "java.desktop,java.sql,java.naming,java.management,java.instrument," +
                        "java.logging,java.xml,jdk.unsupported,jdk.crypto.ec,jdk.crypto.cryptoki," +
                        "jdk.zipfs,jdk.management",
                    "--output", runtimeDir.absolutePath,
                    "--no-header-files", "--no-man-pages", "--compress=2",
                )
                errorOutput = err
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                val msg = err.toString().trim().take(600)
                println("::error::packageReleaseAppImage: jlink failed (exit ${result.exitValue}): $msg")
                throw GradleException("packageReleaseAppImage: jlink failed (exit ${result.exitValue}): $msg")
            }
        }

        // Launchers: double-clickable (javaw, no console) + debug console bat.
        imageDir.resolve("MorseCode.bat").writeText(
            "@echo off\r\n" +
                "rem Morse Code launcher (no console window).\r\n" +
                "set \"DIR=%~dp0\"\r\n" +
                "start \"\" \"%DIR%runtime\\bin\\javaw.exe\" -cp \"%DIR%app\\*\" net.morsecode.desktop.MainKt %*\r\n",
        )
        imageDir.resolve("Start MorseCode (debug console).bat").writeText(
            "@echo off\r\n" +
                "rem Runs Morse Code with a visible console so startup errors are shown.\r\n" +
                "set \"DIR=%~dp0\"\r\n" +
                "\"%DIR%runtime\\bin\\java.exe\" -cp \"%DIR%app\\*\" net.morsecode.desktop.MainKt %*\r\n" +
                "echo.\r\n" +
                "echo Morse Code exited with code %ERRORLEVEL%.\r\n" +
                "pause\r\n",
        )
        // Remove compose-generated launcher artifacts (their cfg references the
        // obfuscated/renamed jars which we intentionally do not ship).
        imageDir.resolve("MorseCode.exe").delete()
        imageDir.resolve("app/MorseCode.cfg").delete()

        // Run after compose's packaging tasks so nothing overwrites this layout.
        afterEvaluate {
            tasks.findByName("packageDistributionForCurrentOS")?.let { mustRunAfter(it) }
            tasks.findByName("createReleaseDistributable")?.let { mustRunAfter(it) }
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
    val hasExeLauncher = dir.resolve("MorseCode.exe").isFile
    val hasBatLauncher = dir.resolve("MorseCode.bat").isFile
    if (!hasExeLauncher && !hasBatLauncher) add("missing launcher: MorseCode.exe or MorseCode.bat")
    val cfg = dir.resolve("app/MorseCode.cfg")
    if (!cfg.isFile && hasExeLauncher) {
        add("missing app/MorseCode.cfg")
    } else if (!cfg.isFile) {
        // bat launcher layout: no cfg needed
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
    val appJars = dir.resolve("app").listFiles()?.filter { it.isFile && it.extension == "jar" } ?: emptyList()
    if (appJars.isEmpty()) {
        add("no jars in app/")
    } else {
        println("validateAppImage[${dir.parentFile.parentFile.name}]: jar inventory:")
        appJars.forEach { jar ->
            val entries = try {
                ZipFile(jar).use { zf -> zf.size() }
            } catch (_: Exception) {
                -1
            }
            println("  ${jar.name} (${jar.length()} bytes, $entries entries)")
        }
        // The launcher loads net.morsecode.desktop.MainKt by name; verify it
        // is really present in one of the packaged jars.
        var hasMainClass = false
        var hasDesktopPkg = false
        for (jar in appJars) {
            try {
                ZipFile(jar).use { zf ->
                    val all = zf.entries().asSequence().map { it.name }.toList()
                    if (!hasMainClass) hasMainClass = all.contains("net/morsecode/desktop/MainKt.class")
                    if (!hasDesktopPkg) hasDesktopPkg = all.any { it.startsWith("net/morsecode/desktop/") }
                }
            } catch (_: Exception) {
            }
        }
        if (!hasMainClass) {
            add("net.morsecode.desktop.MainKt not found in any jar in app/ (any desktop pkg class present: $hasDesktopPkg)")
        }
    }
}
