import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

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
