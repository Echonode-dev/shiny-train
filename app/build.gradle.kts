plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.cleandroid.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cleandroid.app"
        // Android 10 (Q) .. Android 17 and newer forward-compatible.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // Ship English resources only -> smaller APK on low-end devices.
        resourceConfigurations += listOf("en")
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            // Full R8 shrinking: critical for low-end devices (smaller APK, faster startup).
            isMinifyEnabled = true
            isShrinkResources = true
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

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Deliberately tiny dependency set: no DI framework, no image loader, no network stack.
    // Everything heavy (coroutines dispatchers, JSON) uses what the platform already provides.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
