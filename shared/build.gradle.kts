import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        // Code shared by both JVM-based targets (JmDNS discovery base).
        // Registered as an extra source root (no custom dependsOn hierarchy).
        androidMain { kotlin.srcDir("src/jvmShared/kotlin") }
        jvmMain { kotlin.srcDir("src/jvmShared/kotlin") }

        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
            // Both targets are JVM-based, so JVM-only libraries (Ktor CIO,
            // ZXing) resolve fine from commonMain.
            implementation("io.ktor:ktor-server-core:2.3.12")
            implementation("io.ktor:ktor-server-cio:2.3.12")
            implementation("io.ktor:ktor-server-websockets:2.3.12")
            implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
            implementation("io.coil-kt.coil3:coil:3.0.4")
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
            implementation("com.google.zxing:core:3.5.3")
            api("app.cash.sqldelight:runtime:2.0.2")
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("org.jmdns:jmdns:3.5.9")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
            implementation("app.cash.sqldelight:android-driver:2.0.2")
            implementation("androidx.activity:activity-compose:1.9.3")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
            implementation("androidx.media3:media3-exoplayer:1.4.1")
            implementation("androidx.media3:media3-ui:1.4.1")
            implementation("androidx.media3:media3-session:1.4.1")
            implementation("androidx.camera:camera-core:1.3.4")
            implementation("androidx.camera:camera-camera2:1.3.4")
            implementation("androidx.camera:camera-lifecycle:1.3.4")
            implementation("androidx.camera:camera-view:1.3.4")
        }
        jvmMain.dependencies {
            implementation("org.jmdns:jmdns:3.5.9")
            implementation("app.cash.sqldelight:sqlite-driver:2.0.2")
            implementation("uk.co.caprica:vlcj:4.8.2")
        }
    }
}

android {
    namespace = "net.morsecode.shared"
    compileSdk = 35
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("MorseDb") {
            packageName.set("net.morsecode.db")
        }
    }
}

// Safety net: a hung test must not stall the whole CI run.
tasks.withType<Test> {
    timeout.set(java.time.Duration.ofMinutes(5))
    testLogging {
        events("failed", "skipped")
        setExceptionFormat("full")
    }
}
