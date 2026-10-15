import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// projects.json at the repo root holds the developer's own projects. It seeds DEBUG builds only: a release (Google Play)
// must start empty, or every install would ship these projects and email addresses. It's kept out of git; without it
// the demo projects (store/play/source/demo-projects.json) seed the debug build instead.
val deckAssets = layout.buildDirectory.dir("generated/deck-assets")
val syncProjects by tasks.registering(Copy::class) {
    val own = rootProject.file("projects.json")
    from(if (own.exists()) own else rootProject.file("store/play/source/demo-projects.json")) { rename { "projects.json" } }
    into(deckAssets)
}
tasks.named("preBuild") { dependsOn(syncProjects) }

// `-Plegacy` builds the one-time bridge release under the app's original id (com.yonidev.projectdeck), so the
// installed copy can export its projects before moving to com.yonidev.ydeck. Labelled "(ישן)" so the two are told apart.
val legacy = project.hasProperty("legacy")

// Google Play upload key, outside the repo (~/.android-keys, like YVoice). Never commit it; back the folder up.
val uploadProps = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.android-keys/ydeck-upload.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.yonidev.ydeck"
    compileSdk = 36

    defaultConfig {
        applicationId = if (legacy) "com.yonidev.projectdeck" else "com.yonidev.ydeck"
        minSdk = 31
        targetSdk = 36
        versionCode = 13
        versionName = "0.9.4"
        resValue("string", "app_name", if (legacy) "YDeck (ישן)" else "YDeck")
    }

    signingConfigs {
        if (uploadProps.isNotEmpty()) {
            create("upload") {
                storeFile = file(uploadProps.getProperty("storeFile"))
                storePassword = uploadProps.getProperty("storePassword")
                keyAlias = uploadProps.getProperty("keyAlias")
                keyPassword = uploadProps.getProperty("keyPassword")
            }
        }
    }

    sourceSets["debug"].assets.srcDir(deckAssets)
    // The computer link (pairing, sealing, the relay), the same source as the Windows app's.
    sourceSets["main"].java.srcDir(rootProject.file("shared/kotlin"))

    buildTypes {
        release {
            // Phones only: the built-in scanner's native library is ~5 MB per ABI, and x86 is for emulators (debug).
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // The upload key, from 0.6 on. The legacy bridge stays debug-signed: it must install over the old debug-signed app.
            signingConfig = if (legacy) signingConfigs.getByName("debug")
                else signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Scanning the computer's QR code with Google's scanner: the camera stays in Play services, so no camera permission.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    // When Google's scanner can't open (its module not downloaded, e.g. behind a filter): our own, bundled, offline.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    // Fetching the computer's latest state in the background, for the widget.
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    testImplementation("junit:junit:4.13.2")
    // The real org.json on the JVM, so the parser and serializer can be unit-tested without a device.
    testImplementation("org.json:json:20240303")
}
