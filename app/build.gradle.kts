import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val releaseStoreFilePath = keystoreProps.getProperty("storeFile")
    ?: System.getenv("EGGLYTICS_STORE_FILE")
val releaseStorePassword = keystoreProps.getProperty("storePassword")
    ?: System.getenv("EGGLYTICS_STORE_PASSWORD")
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")
    ?: System.getenv("EGGLYTICS_KEY_ALIAS")
val releaseKeyPassword = keystoreProps.getProperty("keyPassword")
    ?: System.getenv("EGGLYTICS_KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFilePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

android {
    namespace = "com.egglytics"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.egglytics"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BUSINESS_ID", "\"egglytics_default\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFilePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            val emulatorHost = (project.findProperty("EMULATOR_HOST") as String?) ?: "10.0.2.2"
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "true")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"$emulatorHost\"")
            buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", "9099")
            buildConfigField("int", "FIRESTORE_EMULATOR_PORT", "8080")
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("boolean", "USE_FIREBASE_EMULATOR", "false")
            buildConfigField("String", "FIREBASE_EMULATOR_HOST", "\"\"")
            buildConfigField("int", "FIREBASE_AUTH_EMULATOR_PORT", "0")
            buildConfigField("int", "FIRESTORE_EMULATOR_PORT", "0")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
