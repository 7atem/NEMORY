plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }
    
    sourceSets.androidMain.dependencies {
        implementation(libs.biometric)
        implementation("androidx.fragment:fragment-ktx:1.8.5")
        implementation(libs.security.crypto)
        implementation(libs.workmanager.runtime.ktx)
        implementation("androidx.core:core-ktx:1.15.0")
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            // Deliberate choice: dynamic framework so the iOS app can link and embed it
            // using a standard FRAMEWORK_SEARCH_PATHS / -framework shared setup.
            baseName = "shared"
            isStatic = false
            binaryOption("bundleId", "com.vaultbrain.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            api(libs.room.runtime)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

android {
    namespace = "com.vaultbrain.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}
