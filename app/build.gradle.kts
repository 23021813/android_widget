import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use(keystoreProperties::load)
}

fun signingSecret(name: String): String? {
    val propertyValue = keystoreProperties.getProperty(name)?.trim().orEmpty()
    if (propertyValue.isNotEmpty()) return propertyValue
    return System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
}

val releaseStoreFilePath = signingSecret("RELEASE_STORE_FILE")
val releaseStorePassword = signingSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = signingSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = signingSecret("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.carlauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.carlauncher"
        minSdk = 28
        targetSdk = 34
        versionCode = 24
        versionName = "1.5.2"
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFilePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    debugImplementation(libs.compose.ui.tooling)

    // Activity & Navigation
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // Lifecycle
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coil
    implementation(libs.coil.compose)

    // Accompanist
    implementation(libs.accompanist.systemuicontroller)

    // Retrofit + Moshi
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.moshi)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.coroutines.android)

    // Core
    implementation(libs.core.ktx)

    testImplementation("junit:junit:4.13.2")
}
