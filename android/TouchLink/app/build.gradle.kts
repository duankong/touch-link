plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ── Version auto-increment ─────────────────────────────────────
val versionFile = rootProject.file("version.properties")
fun readNum(f: java.io.File): Int = try {
    f.readLines().firstOrNull { it.startsWith("buildNumber=") }
        ?.substringAfter("=")?.trim()?.toInt() ?: 0
} catch (_: Exception) { 0 }
fun writeNum(f: java.io.File, n: Int) {
    val lines = f.readLines().filter { !it.startsWith("buildNumber=") }
    f.writeText((lines + "buildNumber=$n").joinToString("\n") + "\n")
}
val buildNumber = readNum(versionFile).let { prev ->
    val next = if (prev <= 0) 1 else prev + 1
    writeNum(versionFile, next)
    next
}

android {
    namespace = "com.touchlink"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.touchlink"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "0.1.${buildNumber}"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
