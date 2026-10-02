import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun getReleaseProperty(key: String, envKey: String): String? {
    return localProperties.getProperty(key)
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv(envKey)?.takeIf { it.isNotBlank() }
}

val storeFileProp = getReleaseProperty("storeFile", "WISHTUBE_STORE_FILE")
val storePasswordProp = getReleaseProperty("storePassword", "WISHTUBE_STORE_PASSWORD")
val keyAliasProp = getReleaseProperty("keyAlias", "WISHTUBE_KEY_ALIAS")
val keyPasswordProp = getReleaseProperty("keyPassword", "WISHTUBE_KEY_PASSWORD")

val hasReleaseSigning = storeFileProp != null && storePasswordProp != null && keyAliasProp != null && keyPasswordProp != null

gradle.taskGraph.whenReady {
    val isReleaseExecution = allTasks.any { 
        it.name == "assembleRelease" || 
        it.name == "bundleRelease" || 
        it.name == "publishRelease" ||
        it.name.startsWith("packageRelease") ||
        it.name.startsWith("signRelease")
    }
    if (isReleaseExecution && !hasReleaseSigning && System.getenv("CI") == "true") {
        throw GradleException("FATAL: Release signing is required on CI/tag builds, but signing configuration is missing.")
    }
}

android {
    namespace = "app.wishtube"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.wishtube"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(storeFileProp!!)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                logger.warn("WARNING: Release signing configuration missing. Falling back to debug signing for local release build.")
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // All Apache-2.0 (see THIRD_PARTY_LICENSES.md)
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("dev.chrisbanes.haze:haze:1.3.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
