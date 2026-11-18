plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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

if (listOf(signingStoreFile, signingStorePassword, signingKeyAlias, signingKeyPassword).any { it.isNullOrBlank() }) {
    throw GradleException(
        "Shared T2S signing credentials are required. Set T2S_KEYSTORE_FILE, " +
            "T2S_KEYSTORE_PASSWORD, T2S_KEY_ALIAS and T2S_KEY_PASSWORD."
    )
}

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

    signingConfigs {
        create("shared") {
            storeFile = file(signingStoreFile!!)
            storePassword = signingStorePassword
            keyAlias = signingKeyAlias
            keyPassword = signingKeyPassword
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("shared")
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
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
