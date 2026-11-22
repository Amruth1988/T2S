plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

val signingStoreFile = providers.gradleProperty("T2S_KEYSTORE_FILE")
    .orElse(providers.environmentVariable("T2S_KEYSTORE_FILE"))
    .orNull
val signingStorePassword = providers.gradleProperty("T2S_KEYSTORE_PASSWORD")
    .orElse(providers.environmentVariable("T2S_KEYSTORE_PASSWORD"))
    .orNull
val signingKeyAlias = providers.gradleProperty("T2S_KEY_ALIAS")
    .orElse(providers.environmentVariable("T2S_KEY_ALIAS"))
    .orNull
val signingKeyPassword = providers.gradleProperty("T2S_KEY_PASSWORD")
    .orElse(providers.environmentVariable("T2S_KEY_PASSWORD"))
    .orNull

val hasSharedSigning = listOf(
    signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.t2sstudio.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.t2sstudio.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    if (hasSharedSigning) {
        signingConfigs {
            create("shared") {
                storeFile = file(signingStoreFile!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasSharedSigning) signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            if (hasSharedSigning) signingConfig = signingConfigs.getByName("shared")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.media3:media3-exoplayer:1.8.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
