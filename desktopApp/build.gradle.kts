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
            modules("java.sql", "java.naming", "jdk.crypto.ec", "jdk.crypto.cryptoki", "jdk.zipfs", "jdk.management")
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
// the release app-image task (falling back to the debug-variant task).
tasks.register("packageReleaseAppImage") {
    group = "compose desktop"
    description = "Alias for createReleaseDistributable/createDistributable (CI compatibility)."
    dependsOn(tasks.findByName("createReleaseDistributable") ?: tasks.named("createDistributable"))
}
