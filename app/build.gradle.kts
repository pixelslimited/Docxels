plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.docscan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.docscan.app"
        // minSdk 29 lets us save to Pictures/Downloads through MediaStore with no storage permission.
        minSdk = 29
        targetSdk = 35
        // CI bumps this on every build so each new APK installs over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
        // OpenCV and ML Kit ship native libraries per ABI, and that is most of the app's size.
        // Every phone made in the last ~8 years is arm64; add "armeabi-v7a" back only for very old phones.
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        // One fixed key so every build (local or CI) can be installed over the previous one.
        // This is for sideloading only; Play Store uploads need their own secret key.
        create("sideload") {
            storeFile = file("sideload.jks")
            storePassword = "docscan"
            keyAlias = "docscan"
            keyPassword = "docscan"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("sideload") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("sideload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        // Compress native libraries inside the APK (much smaller download than the uncompressed default).
        jniLibs { useLegacyPackaging = true }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")

    // Camera (live preview, capture, live edge overlay)
    val camerax = "1.4.0"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Local database for documents and pages
    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Image processing: edge detection, perspective warp, filters, hand removal, table lines.
    implementation("org.opencv:opencv:4.9.0")

    // On-device OCR (bundled model, works offline): table cells, editable Word text, page-order detection.
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
}
