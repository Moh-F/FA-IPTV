import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun prop(name: String): String = providers.gradleProperty(name).get()

// Signing: from key.properties (local) or env vars (CI). Never commit the keystore.
val keyProps = Properties().apply {
    val f = rootProject.file("key.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signValue(name: String, env: String): String? = System.getenv(env) ?: keyProps.getProperty(name)

// Launcher icon, splash logo and TV banner are drawn at build time with the version on a sash.
val appIcons = tasks.register<IconTask>("generateAppIcons") {
    versionName.set(prop("VERSION_NAME"))
    outputDir.set(layout.buildDirectory.dir("generated/appicons"))
}

android {
    namespace = "com.mohdev.faiptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mohdev.faiptv"
        minSdk = 24          // Fire OS 6 (Android 7.1), phones and Google TV
        targetSdk = 35
        versionCode = (providers.gradleProperty("versionCode").orNull ?: prop("VERSION_CODE")).toInt()
        versionName = prop("VERSION_NAME")
        buildConfigField("String", "GITHUB_OWNER", "\"${prop("GITHUB_OWNER")}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${prop("GITHUB_REPO")}\"")
    }

    val storePath = signValue("storeFile", "KEYSTORE_PATH")
    if (storePath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(storePath)
                storeType = "pkcs12"
                storePassword = signValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = signValue("keyAlias", "KEY_ALIAS")
                keyPassword = signValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // One APK per CPU family (VLC engine is native code). The updater picks the right one.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        warningsAsErrors = false
        disable += setOf("UnsafeOptInUsageError", "MissingTranslation")
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/versions/9/previous-compilation-data.bin")
        jniLibs.useLegacyPackaging = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(appIcons) { it.outputDir }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource-okhttp:$media3")

    implementation("org.videolan.android:libvlc-all:3.6.2")   // LGPL-2.1, dynamically linked
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.7.3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
