plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.calecutech.ramayanam"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.calecutech.ramayanam"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "1.4"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

// No libraries: the app uses only the Android framework and the Kotlin standard library.
dependencies {}
